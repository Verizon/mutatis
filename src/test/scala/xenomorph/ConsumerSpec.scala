package xenomorph

import java.util.concurrent.atomic.AtomicReference

import kafka.producer._
import kafka.serializer.StringDecoder

import scala.concurrent.TimeoutException
import scalaz.stream._
import scalaz.concurrent.Task

class ConsumerSpec extends UnitSpec with EmbeddedKafkaBuilder {
  val dataStore = new AtomicReference[List[String]](List.empty[String])

  val data: List[(Int, String)] = (1 to 10).toList.map { num =>
    num -> s"test-message-$num"
  }

  val messages: List[KeyedMessage[Array[Byte], Array[Byte]]] = data.map {
    case (num, message) =>
      new KeyedMessage(topic, num.toString.getBytes(), message.getBytes())
  }

  "Consumer should" - {
    "should consume message in order produced" in {
      val producer: Producer[Array[Byte], Array[Byte]] = new Producer(producerConfig)
      // send message
      producer.send(messages: _*)
      producer.close()

      val dataStoreSink: Sink[Task, String] = sink.lift { s =>
        Task.delay {
          val data = dataStore.get()
          dataStore.set(data :+ s.reverse)
          ()
        }
      }

      // this is really ugly, I tried to use .take(X).runLog.run but mergeN seemed to force needing to take n-2
      // elements before process would return, but at least this exercises a known use case
      try {
        consumer[String, Unit](consumerConfig, topic, new StringDecoder(), 1, dataStoreSink).runLog.runFor(1000)
      } catch {
        case _: TimeoutException => ()
      }

      dataStore.get shouldEqual data.map(_._2.reverse)
    }
  }
}
