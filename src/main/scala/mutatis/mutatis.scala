/*
*****************************************
Copyright 2017  Verizon.
               
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
****************************************************************
*/
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent._

import journal.Logger
import kafka.common.TopicAndPartition
import kafka.consumer._
import kafka.message.MessageAndMetadata
import kafka.producer.async.DefaultEventHandler
import kafka.producer._
import kafka.serializer.{Decoder, Encoder, NullEncoder}
import kafka.utils.Utils
import treasurechest._

import scala.concurrent.duration.{Duration, _}
import scalaz.concurrent._
import scalaz.stream.Process._
import scalaz.stream._
import scalaz.stream.time.awakeEvery
import scalaz.{-\/, \/-}

package object mutatis {

  val log = Logger[this.type]
  private implicit val pool: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor(daemonThreads("mutatis-committer"))

  private def daemonThreads(name: String) = new ThreadFactory {
    def newThread(r: Runnable) = {
      val t = Executors.defaultThreadFactory.newThread(r)
      t.setDaemon(true)
      t.setName(name)
      t
    }
  }

  private def init[K, V](
      consumerConfig: ConsumerConfig,
      topic: String,
      keyDecoder: Decoder[K],
      messageDecoder: Decoder[V],
      numStreams: Int,
      refreshTime: Duration = 2.minutes) = {
    val props = consumerConfig.props.props
    props.setProperty("auto.commit.enable", "false")
    val fixedCC = new ConsumerConfig(props)

    val tocc                                 = new TopicOffsetConsumerConnector(fixedCC)
    val consumerConnector: ConsumerConnector = tocc.consumerConnector
    val filterSpec                           = Whitelist(topic)

    val streams =
      consumerConnector.createMessageStreamsByFilter(filterSpec, numStreams, keyDecoder, messageDecoder)

    val commitOffsetMap = new ConcurrentHashMap[TopicAndPartition, Long]()

    val commitOffset: MessageAndMetadata[K, V] => Unit = msg => {
      commitOffsetMap.put(TopicAndPartition(msg.topic, msg.partition), msg.offset)
      ()
    }

    val stopCommitter: AtomicBoolean = new AtomicBoolean(false)

    (Process.eval(commit(tocc, commitOffsetMap)) ++ awakeEvery(refreshTime)
      .evalMap[Task, Unit](_ => commit(tocc, commitOffsetMap))).run
      .runAsyncInterruptibly(_ => (), stopCommitter)

    val shutdown: () => Unit = tocc.consumerConnector.shutdown
    Init(streams, commitOffset, shutdown, stopCommitter)
  }

  def consumer[K, V](
      consumerConfig: ConsumerConfig,
      topic: String,
      keyDecoder: Decoder[K],
      messageDecoder: Decoder[V],
      numStreams: Int,
      refreshTime: Duration = 2.minutes): Process[Task, Process[Task, DecodedEvent[K, V]]] = {
    Process
      .bracket[Task, Init[K, V], Process[Task, DecodedEvent[K, V]]](
        Task.delay(init(consumerConfig, topic, keyDecoder, messageDecoder, numStreams, refreshTime)))(i =>
        eval_(Task.delay(i.stopCommitter.set(true)))) { init =>
        Process
          .emitAll(init.streams)
          .map { stream =>
            streamConsumer(init.commitOffset, init.shutdown)(stream)
          }
      }
  }

  private def commit(
      tocc: TopicOffsetConsumerConnector,
      commitOffsetMap: ConcurrentHashMap[TopicAndPartition, Long]): Task[Unit] =
    Task {
      val commitOffsetsIter = commitOffsetMap.entrySet().iterator()
      while (commitOffsetsIter.hasNext) {
        val commitOffset = commitOffsetsIter.next()
        tocc.commitOffset(commitOffset.getKey, commitOffset.getValue)
      }
    }.attempt.map {
      case -\/(e) => log.error("commit failed", e)
      case \/-(_) => ()
    }

  private def streamConsumer[K, V](commitOffset: MessageAndMetadata[K, V] => Unit, shutdown: () => Unit)(
      stream: KafkaStream[K, V]): Process[Task, DecodedEvent[K, V]] = {
    Process
      .bracket[Task, ConsumerIterator[K, V], DecodedEvent[K, V]](Task.delay(stream.iterator())) { consumer =>
        eval_(Task.delay(shutdown()))
      } { consumer =>
        val begin = eval_(Task delay {
          log.info(s"${Thread.currentThread()} - Start pulling records from Kafka.")
        })

        val process: Process[Task, DecodedEvent[K, V]] =
          syncPoll(DecodedEvent(consumer.next)).flatMap { message =>
            Process
              .emit(message)
              .onComplete(commit(commitOffset, message.messageAndMetadata).drain)
          }

        begin ++ process
      }
  }

  private def commit[K, V](
      commit: MessageAndMetadata[K, V] => Unit,
      msg: MessageAndMetadata[K, V]): Process[Task, Unit] =
    Process eval Task.delay {
      log.debug(
        s"${Thread.currentThread()} - Committing offset=${msg.offset} topic=${msg.topic} partition=${msg.partition}")
      commit(msg)
    }

  private def syncPoll[K, V](blockingTask: => DecodedEvent[K, V]): Process[Task, DecodedEvent[K, V]] = {
    val t = Task.delay(blockingTask)

    Process repeatEval t
  }

  def producer[V](cfg: ProducerConfig, topic: String, msgEncoder: Encoder[V]): Sink[Task, V] = {

    val prod = producer(cfg, None, msgEncoder)

    sink
      .lift[Task, V] { v =>
        Task.delay[Unit] {
          log.debug(s"${Thread.currentThread()} sending event for value=$v")

          prod.send(new KeyedMessage(topic, v))
        }
      }
      .onComplete {
        Process.suspend {
          log.info("End of stream. Closing producer")
          prod.close
          Process.halt
        }
      }
  }

  def producer[K, V](
      cfg: ProducerConfig,
      topic: String,
      keyEncoder: Encoder[K],
      msgEncoder: Encoder[V]): Sink[Task, (K, V)] = {

    val prod = producer(cfg, Some(keyEncoder), msgEncoder)

    sink
      .lift[Task, (K, V)] {
        case (k, v) =>
          Task.delay[Unit] {
            log.debug(s"${Thread.currentThread()} sending event for key=$k and value=$v")

            prod.send(new KeyedMessage(topic, k, v))
          }
      }
      .onComplete {
        Process.suspend {
          log.info("End of stream. Closing producer")
          prod.close
          Process.halt
        }
      }
  }

  private def producer[K, V](
      cfg: ProducerConfig,
      keyEncoder: Option[Encoder[K]],
      msgEncoder: Encoder[V]): Producer[K, V] = {
    new Producer[K, V](
      cfg,
      new DefaultEventHandler[K, V](
        cfg,
        Utils.createObject[Partitioner](cfg.partitionerClass, cfg.props),
        msgEncoder,
        keyEncoder.getOrElse(new NullEncoder[K]()),
        new ProducerPool(cfg))
    )
  }
}
