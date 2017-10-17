//: ----------------------------------------------------------------------------
//: Copyright (C) 2015 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------
package mutatis

import java.util.concurrent.atomic.AtomicReference

import kafka.producer._
import kafka.serializer.{Decoder, DefaultDecoder, StringDecoder}
import scala.concurrent.duration._
import scalaz.{-\/, \/-}
import scalaz.stream._
import scalaz.concurrent.Task
import common.KafkaTestHelper._

class ConsumerSpec extends UnitSpec with EmbeddedKafkaBuilder {
  type Bytes = Array[Byte]

  val bytesDecoder  = new DefaultDecoder()
  val stringDecoder = new StringDecoder()

  val dataStore = new AtomicReference[List[String]](List.empty[String])

  val data: List[(Int, String)] = (1 to 16).toList.map { num =>
    num -> s"test-message-$num"
  }

  val messages: List[KeyedMessage[Array[Byte], Array[Byte]]] = data.map {
    case (num, message) =>
      new KeyedMessage(topic, num.toString.getBytes(), message.getBytes())
  }

  def produce(): Unit = {
    val producer: Producer[Array[Byte], Array[Byte]] = new Producer(producerConfig)
    producer.send(messages: _*)
    producer.close()
  }

  "Consumer should" - {
    "should consume message in order produced" in {
      produce()

      val dataStoreSink: Sink[Task, DecodedEvent[Bytes, String]] = sink.lift { s =>
        Task.delay {
          val data = dataStore.get()
          dataStore.set(data :+ s.message.reverse)
          ()
        }
      }

      consumer[Bytes, String](consumerConfig, topic, bytesDecoder, stringDecoder, 1).flatMap { s =>
        s through dataStoreSink
      }.take(messages.size).runLog.attempt.run

      dataStore.get shouldEqual data.map(_._2.reverse)
    }

    "should consume message in order produced and commit periodically" in {
      produce()

      val dataStoreSink: Sink[Task, DecodedEvent[Bytes, String]] = sink.lift { s =>
        Task.delay {
          val data = dataStore.get()
          dataStore.set(data :+ s.message.reverse)
          Thread.sleep(200)
          ()
        }
      }

      consumer[Bytes, String](consumerConfig, topic, bytesDecoder, stringDecoder, 1, 150.millisecond).flatMap { s =>
        s through dataStoreSink
      }.take(messages.size).runLog.attempt.run

      // Why 14 if there we are taking 16 messages
      // 1) Offset are 0 indexed. Now, why 15 :)
      // 2) The connection is closed immediately after the last message is consumed.
      // This gives no time to commit the last one
      getOffset(zkClient, groupId, topic, 0) shouldBe "14"
    }

    "should handle exceptions in decoder" in {
      produce()

      val badDecoder = new Decoder[String] {
        override def fromBytes(bytes: Bytes): String = new String(bytes, "UTF8").toInt.toString
      }

      val seq = consumer[Bytes, String](consumerConfig, topic, bytesDecoder, badDecoder, 1)
        .flatMap(a => a)
        .take(1)
        .runLog
        .attempt
        .run

      seq match {
        case \/-(_) => fail("should not be success")
        case -\/(e) => e shouldBe a[NumberFormatException]
      }
    }
  }
}
