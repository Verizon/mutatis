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

  def consumer[A, B](
      consumerConfig: ConsumerConfig,
      topic: String,
      decoder: Decoder[A],
      numStreams: Int,
      throughThenCommit: Process[Task, A => Task[B]])(implicit S: Strategy): Process[Task, B] = {
    val tocc                                 = new TopicOffsetConsumerConnector(consumerConfig)
    val consumerConnector: ConsumerConnector = tocc.consumerConnector
    val filterSpec                           = Whitelist(topic)

    val streams =
      consumerConnector.createMessageStreamsByFilter(filterSpec, numStreams, new DefaultDecoder(), decoder)

    val p = Process.emitAll(streams).map { stream =>
      streamConsumer(
        (msg: MessageAndMetadata[Array[Byte], A]) =>
          tocc.commitOffset(TopicAndPartition(msg.topic, msg.partition), msg.offset),
        tocc.consumerConnector.shutdown)(stream) through throughThenCommit
    }

    merge.mergeN(numStreams)(p)(S)
  }

  def streamConsumer[A, B](commitOffset: MessageAndMetadata[Array[Byte], A] => Unit, shutdown: () => Unit)(
      stream: KafkaStream[Array[Byte], A]) = {
    Process
      .bracket[Task, ConsumerIterator[Array[Byte], A], A](Task.delay(stream.iterator())) { consumer =>
        eval_(Task.delay(shutdown()))
      } { consumer =>
        val startMsg = eval_(Task delay {
          log.info(s"${Thread.currentThread()} - Start pulling records from Kafka.")
        })

        val pullFromKafka =
          syncPoll(consumer.next).flatMap { res =>
            Process
              .emit(res.message)
              .toSource
              .onComplete(commit(commitOffset, res).drain)
          }

        val pullFromKafkaProcess = pullFromKafka.onHalt(
          cause =>
            cause.fold(Process.empty[Task, A])(c =>
              eval_(Task.delay {
                log.info(s"Polling from kafka was interrupted by [$c]")
              }) ++ pullFromKafka)
        )

        startMsg ++ pullFromKafkaProcess
      }
  }

  private def commit[T](
      commit: MessageAndMetadata[Array[Byte], T] => Unit,
      msg: MessageAndMetadata[Array[Byte], T]): Process[Task, Unit] =
    Process eval Task.delay {
      log.debug(
        s"${Thread.currentThread()} - Committing offset=${msg.offset} topic=${msg.topic} partition=${msg.partition}")
      commit(msg)
    }

  private def logThrowable[T]: PartialFunction[Throwable \/ T, Option[T]] = {
    case -\/(err) =>
      log.error("Failure while processing record.", err)
      None
    case \/-(v) => Some(v)
  }

  private def collectSome[T]: PartialFunction[Option[T], T] = {
    case Some(v) => v
  }

  private def syncPoll[T](
      blockingTask: => MessageAndMetadata[Array[Byte], T]): Process[Task, MessageAndMetadata[Array[Byte], T]] = {
    val t = Task.delay(blockingTask)

    (Process repeatEval t).onHalt(
      cause =>
        eval_(Task.delay {
          log.info(s"Polling from kafka was halted by [$cause]")
        })
    )
  }
}
