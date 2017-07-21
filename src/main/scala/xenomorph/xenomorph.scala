import journal.Logger
import kafka.common.TopicAndPartition
import kafka.consumer._
import kafka.message.MessageAndMetadata
import kafka.serializer.{Decoder, DefaultDecoder}

import scalaz.concurrent._
import scalaz.stream._
import scalaz.stream.Process._
import treasurechest._

import scalaz.{-\/, \/, \/-}

package object xenomorph {
  val log = Logger[this.type]

  def consumer[K, V](
      consumerConfig: ConsumerConfig,
      topic: String,
      keyDecoder: Decoder[K],
      messageDecoder: Decoder[V],
      numStreams: Int): Process[Task, Process[Task, Throwable \/ V]] = {
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
      stream: KafkaStream[K, V]): Process[Task, Throwable \/ V] = {
    Process
      .bracket[Task, ConsumerIterator[K, V], Throwable \/ V](Task.delay(stream.iterator())) { consumer =>
        eval_(Task.delay(shutdown()))
      } { consumer =>
        val begin = eval_(Task delay {
          log.info(s"${Thread.currentThread()} - Start pulling records from Kafka.")
        })

        val process: Process[Task, \/[Throwable, V]] =
          syncPoll(DecodedMessage(consumer.next)).flatMap { message =>
            Process
              .emit(message.map(_.message))
              .onComplete((message match {
                case \/-(message) => commit(commitOffset, message.messageAndMetadata)
                case -\/(_)       => Halt(Cause.End)
              }).drain)
          }

        val end = process.onHalt(
          cause =>
            cause.fold(Process.empty[Task, Throwable \/ V])(c =>
              eval_(Task.delay {
                log.info(s"Polling from kafka was interrupted by [$c]")
              }))
        )

        begin ++ end
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

  private def syncPoll[K, V](blockingTask: => DecodedMessage[K, V]): Process[Task, Throwable \/ DecodedMessage[K, V]] = {
    val t = Task.delay(blockingTask).attempt

    (Process repeatEval t).onHalt(
      cause =>
        eval_(Task.delay {
          log.info(s"Polling from kafka was halted by [$cause]")
        })
    )
  }
}
