import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent._
import journal.Logger
import kafka.common.TopicAndPartition
import kafka.consumer._
import kafka.message.MessageAndMetadata
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}
import kafka.serializer.{Decoder, Encoder}
import treasurechest._

import scala.concurrent.duration.{Duration, _}
import scalaz.concurrent._
import scalaz.stream.Process._
import scalaz.stream._
import scalaz.stream.time.awakeEvery
import scalaz.{-\/, \/-}

package object xenomorph {

  val log = Logger[this.type]
  private implicit val pool: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor(
      daemonThreads("xenomorph-committer"))

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
      consumerConnector.createMessageStreamsByFilter(
        filterSpec,
        numStreams,
        keyDecoder,
        messageDecoder)

    val commitOffsetMap = new ConcurrentHashMap[TopicAndPartition, Long]()

    val commitOffset: MessageAndMetadata[K, V] => Unit = msg => {
      commitOffsetMap.put(
        TopicAndPartition(msg.topic, msg.partition),
        msg.offset)
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
      refreshTime: Duration = 2.minutes): Process[
    Task,
    Process[Task, DecodedEvent[K, V]]] = {
    Process
        .bracket[Task, Init[K, V], Process[Task, DecodedEvent[K, V]]](
      Task.delay(
        init(
          consumerConfig,
          topic,
          keyDecoder,
          messageDecoder,
          numStreams,
          refreshTime)))(i => eval_(Task.delay(i.stopCommitter.set(true)))) { init =>

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

  private def streamConsumer[K, V](
      commitOffset: MessageAndMetadata[K, V] => Unit,
      shutdown: ()                           => Unit)(
      stream: KafkaStream[K, V]): Process[Task, DecodedEvent[K, V]] = {
    Process
      .bracket[Task, ConsumerIterator[K, V], DecodedEvent[K, V]](
        Task.delay(stream.iterator())) { consumer =>
        eval_(Task.delay(shutdown()))
      } { consumer =>
        val begin = eval_(Task delay {
          log.info(
            s"${Thread.currentThread()} - Start pulling records from Kafka.")
        })

        val process: Process[Task, DecodedEvent[K, V]] =
          syncPoll(DecodedEvent(consumer.next)).flatMap { message =>
            Process
              .emit(message)
              .onComplete(
                commit(commitOffset, message.messageAndMetadata).drain)
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

  private def syncPoll[K, V](blockingTask: => DecodedEvent[K, V]): Process[
    Task,
    DecodedEvent[K, V]] = {
    val t = Task.delay(blockingTask)

    Process repeatEval t
  }

  def producer[A](
      cfg: ProducerConfig,
      topic: String,
      keyEncoder: Encoder[A],
      msgEncoder: Encoder[A]): Sink[Task, A] = {
    val producer = new Producer[Array[Byte], Array[Byte]](cfg)

    sink
      .lift[Task, A] { a: A =>
        Task.delay[Unit] {
          log.info(s"${Thread.currentThread()} sending event -  $a")

          producer.send(
            new KeyedMessage[Array[Byte], Array[Byte]](
              topic,
              keyEncoder.toBytes(a),
              msgEncoder.toBytes(a)))
        }
      }
      .onComplete {
        Process.suspend {
          log.info("End of stream. Closing producer")
          producer.close
          Process.halt
        }
      }
  }
}
