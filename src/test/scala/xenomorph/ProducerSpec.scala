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

import kafka.consumer.{Consumer, Whitelist}
import kafka.serializer._

import scalaz.concurrent.Task
import scalaz.{-\/, \/-}
import scalaz.stream._

class ProducerSpec extends UnitSpec with EmbeddedKafkaBuilder {

  val data                         = List("a", "b", "c")
  val dataP: Process[Task, String] = Process.emitAll(data)

  "Producer should" - {
    "produce events that can be consumed" in {

      val sink = dataP to producer[String](cfg = producerConfig, topic = topic, msgEncoder = new StringEncoder)
      sink.runLog.run

      val consumer = Consumer.create(consumerConfig)
      val records = consumer
        .createMessageStreamsByFilter[String, String](Whitelist(topic), 1, new StringDecoder, new StringDecoder)
        .flatMap { stream =>
          stream.iterator.take(3).map(_.message)
        }

      records shouldEqual (data)
    }
  }

  "stop processing and return when the data cannot be encoded" in {
    val badEncoder = new Encoder[String] {
      override def toBytes(bytes: String): Array[Byte] = throw new RuntimeException("oh no")
    }

    val sink = dataP to producer[String](cfg = producerConfig, topic = topic, msgEncoder = badEncoder)
    sink.runLog.attempt.run match {
      case -\/(e) => e.getMessage shouldEqual ("oh no")
      case \/-(_) => fail("should not be success")
    }
  }

  "produce events with key that can be consumed" in {
    val dataWithKey = data.map(a => (a, a))

    val sink = Process.emitAll(dataWithKey) to producer[String, String](
        cfg        = producerConfig,
        topic      = topic,
        keyEncoder = new StringEncoder,
        msgEncoder = new StringEncoder)
    sink.runLog.run

    val consumer = Consumer.create(consumerConfig)
    val records = consumer
      .createMessageStreamsByFilter[String, String](Whitelist(topic), 1, new StringDecoder, new StringDecoder)
      .flatMap { stream =>
        stream.iterator.take(3).map(a => (a.key, a.message))
      }

    records shouldEqual (dataWithKey)
  }
}
