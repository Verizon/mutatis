import journal.Logger
import kafka.common.TopicAndPartition
import kafka.consumer._
import kafka.message.MessageAndMetadata
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}
import kafka.serializer.{Decoder, Encoder}
import treasurechest._

import scalaz.concurrent._
import scalaz.stream.Process._
import scalaz.stream._

package object xenomorph {
  val log = Logger[this.type]

  def consumer[K, V](
      consumerConfig: ConsumerConfig,
      topic: String,
      keyDecoder: Decoder[K],
      messageDecoder: Decoder[V],
      numStreams: Int): Process[Task, Process[Task, DecodedEvent[K, V]]] = {
    val tocc                                 = new TopicOffsetConsumerConnector(consumerConfig)
    val consumerConnector: ConsumerConnector = tocc.consumerConnector
    val filterSpec                           = Whitelist(topic)

    val streams =
      consumerConnector.createMessageStreamsByFilter(filterSpec, numStreams, keyDecoder, messageDecoder)

    val commitOffset: MessageAndMetadata[K, V] => Unit = msg => {
      tocc.commitOffset(TopicAndPartition(msg.topic, msg.partition), msg.offset)
    }

    val shutdown: () => Unit = tocc.consumerConnector.shutdown

    Process.emitAll(streams).map { stream =>
      streamConsumer(commitOffset, shutdown)(stream)
    }
  }

  def streamConsumer[K, V](commitOffset: MessageAndMetadata[K, V] => Unit, shutdown: () => Unit)(
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

  def producer[A](cfg: ProducerConfig, topic: String, keyEncoder: Encoder[A], msgEncoder: Encoder[A]): Sink[Task, A] = {
    val producer = new Producer[Array[Byte], Array[Byte]](cfg)

    sink
      .lift[Task, A] { a: A =>
        Task.delay[Unit] {
          log.info(s"${Thread.currentThread()} sending event -  $a")

          producer.send(
            new KeyedMessage[Array[Byte], Array[Byte]](topic, keyEncoder.toBytes(a), msgEncoder.toBytes(a)))
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
