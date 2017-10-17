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

import kafka.serializer.{DefaultDecoder, StringDecoder, StringEncoder}

import scalaz.concurrent.Task
import scalaz.stream.Process

class IntegrationSpec extends UnitSpec with EmbeddedKafkaBuilder {

  "Producer should produce events that can be consumed by the mutatis consumer" in {
    val data                         = List("a", "b", "c")
    val dataP: Process[Task, String] = Process.emitAll(data)

    val sink = dataP to producer[String](cfg = producerConfig, topic = topic, msgEncoder = new StringEncoder)
    sink.runLog.run

    val read =
      consumer[Array[Byte], String](consumerConfig, topic, new DefaultDecoder(), new StringDecoder(), 1)
        .flatMap(s => s)
        .take(3)
        .runLog
        .run

    read.toList.map(_.message) shouldEqual (data)
  }
}
