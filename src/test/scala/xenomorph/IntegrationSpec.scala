package xenomorph

import kafka.serializer.{DefaultDecoder, StringDecoder, StringEncoder}

import scalaz.\/-
import scalaz.concurrent.Task
import scalaz.stream.Process

class IntegrationSpec extends UnitSpec with EmbeddedKafkaBuilder {

  "Producer should produce events that can be consumed by the xenomorph consumer" in {
    val data                         = List("a", "b", "c")
    val dataP: Process[Task, String] = Process.emitAll(data)

    val sink = dataP to producer[String](
      cfg        = producerConfig,
      topic      = topic,
      keyEncoder = new StringEncoder,
      msgEncoder = new StringEncoder)
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
