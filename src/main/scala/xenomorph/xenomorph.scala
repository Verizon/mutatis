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

  def consumer[T](
      consumerConfig: ConsumerConfig,
      topic: String,
      decoder: Decoder[Throwable \/ T],
      numStreams: Int,
      sink: Sink[Task, T])(implicit S: Strategy): Process[Task, Unit] = {
    val tocc                                 = new TopicOffsetConsumerConnector(consumerConfig)
    val consumerConnector: ConsumerConnector = tocc.consumerConnector
    val filterSpec                           = Whitelist(topic)

    val streams =
      consumerConnector.createMessageStreamsByFilter(filterSpec, numStreams, new DefaultDecoder(), decoder)

    val p = Process.emitAll(streams).map { stream =>
      Process
        .bracket[Task, ConsumerIterator[Array[Byte], Throwable \/ T], Throwable \/ T](Task.delay(stream.iterator())) {
          consumer =>
            eval_(Task.delay(consumerConnector.shutdown()))
        } { consumer =>
          val startMsg = eval_(Task delay {
            log.info(s"${Thread.currentThread()} - Start pulling records from Kafka.")
          })

          val pullFromKafka =
            syncPoll(consumer.next).flatMap { res =>
              Process
                .emit(res.message)
                .toSource
                .onComplete {
                  commit(tocc, res).drain
                }
            }

          val pullFromKafkaProcess = pullFromKafka.onHalt(
            cause =>
              cause.fold(Process.empty[Task, Throwable \/ T])(c =>
                eval_(Task.delay {
                  log.info(s"Polling from kafka was interrupted by [$c]")
                }) ++ pullFromKafka)
          )

          startMsg ++ pullFromKafkaProcess
        }
        .map(logThrowable)
        .collect(collectSome) to sink
    }
    merge.mergeN(numStreams)(p)(S)
  }

  private def commit[T](
      consumer: TopicOffsetConsumerConnector,
      msg: MessageAndMetadata[Array[Byte], Throwable \/ T]): Process[Task, Unit] =
    Process eval Task.delay {
      log.debug(
        s"${Thread.currentThread()} - Committing offset=${msg.offset} topic=${msg.topic} partition=${msg.partition}")
      val tp = TopicAndPartition(msg.topic, msg.partition)
      consumer.commitOffset(tp, msg.offset)
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

  private def syncPoll[T](blockingTask: => MessageAndMetadata[Array[Byte], Throwable \/ T])
    : Process[Task, MessageAndMetadata[Array[Byte], Throwable \/ T]] = {
    val t = Task.delay(blockingTask)

    (Process repeatEval t).onHalt(
      cause =>
        eval_(Task.delay {
          log.info(s"Polling from kafka was halted by [$cause]")
        })
    )
  }
}
