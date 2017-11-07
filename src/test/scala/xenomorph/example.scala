//: ----------------------------------------------------------------------------
//: Copyright (C) 2017 Verizon.  All Rights Reserved.
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

/**
 * Goal for this example is to demonstrate use case where a producer generates events faster than consumers can
 * process each event so it is required to load balance across multiple consumers to handle the load.
 *
 * 1. Set up kafka server:
 *    Download and start zk and kafka per https://kafka.apache.org/082/documentation.html#quickstart
 *
 * 2. create a topic called "test8" with 8 partitions:
 *    ./bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 8 --topic test8
 *
 * 3. Start producer - notice one message is produced every 100 milliseconds:
 *    sbt "test:run-main mutatis.ExampleProducer"
 *
 * 4. Start consumer - notice one message takes 250 milliseconds to process, one consumer is not enough:
 *    sbt "test:run-main mutatis.ExampleConsumer"
 *
 * Consideration when setting up number of partitions, consumers, and streams per consumer:
 * - Number of partitions represent the max number of streams you can have across all consumers
 * - Above we created a topic with 8 streams (update config.numberOfTopics as needed)
 * - Consumers are set up to have 2 concurrent streams (update config.streams to change it)
 * - Per above, max number of consumers you can have is 4 (8 topics divided by 2 streams per consumer)
 * - A fifth consumer will not receive any messages from kafka because there will be no partitions to assign to it
 * - Killing one of the 4 active consumer will trigger rebalance and the 5th consumer will pick up the slack
 */
import kafka.serializer.{Decoder, Encoder}
import kafka.consumer.ConsumerConfig
import java.util.Properties
import java.util.concurrent.{ExecutorService, Executors, ScheduledExecutorService}

import kafka.producer.ProducerConfig

import scalaz.concurrent.{Strategy, Task}
import scalaz.stream.{merge, time, Sink}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

object ExampleProducer extends App {
  import config._

  implicit val pool: ScheduledExecutorService = Executors.newScheduledThreadPool(3, Executors.defaultThreadFactory())
  implicit val ec: ExecutionContext           = ExecutionContext.fromExecutor(pool)
  implicit val S: Strategy                    = Strategy.Executor(pool)

  val sink: Sink[Task, Duration] = mutatis.producer(producerCfg, topic, encoder)
  val producer                   = time.awakeEvery(100 milliseconds) to sink
  producer.run.run
}

object ExampleConsumer extends App {
  import config._

  implicit val pool: ExecutorService = Executors.newFixedThreadPool(streams, Executors.defaultThreadFactory())
  implicit val ec: ExecutionContext  = ExecutionContext.fromExecutor(pool)
  implicit val S: Strategy           = Strategy.Executor(pool)

  val consumer = merge.mergeN(streams)(
    // Map the internal stream to worker.process so that we commit to kafka only after processing is done
    mutatis
      .consumer(consumerCfg, topic, keyDecoder, decoder, streams)
      .map(stream =>
        stream.evalMap { event =>
          // this will happen BEFORE commit to kafka
          worker.process(event)
      })
  )(S)

  val consumerWithPostProcessing = consumer.map(_ => () /* this will happen AFTER commit to kafka */ )

  consumerWithPostProcessing.run.run
}

object config {
  val producerProps = new Properties()
  producerProps.put("metadata.broker.list", "localhost:9092")
  producerProps.put("acks", "all")
  producerProps.put("retries", "0")
  producerProps.put("batch.size", "16384")
  producerProps.put("linger.ms", "1")
  producerProps.put("buffer.memory", "33554432")
  producerProps.put("serializer.class", "kafka.serializer.DefaultEncoder")
  val producerCfg = new ProducerConfig(producerProps)

  val consumerProps = new Properties()
  consumerProps.put("zookeeper.connect", "localhost:2181")
  consumerProps.put("auto.offset.reset", "smallest")
  consumerProps.put("consumer.timeout.ms", "60000")
  consumerProps.put("auto.commit.interval.ms", "5000000")
  consumerProps.put("group.id", "test1")
  val consumerCfg = new ConsumerConfig(consumerProps)

  val streams = 2

  val numberOfTopics = 8

  val topic = "test8"

  val encoder = new Encoder[Duration] {
    override def toBytes(t: Duration): Array[Byte] = t.toString.toCharArray.map(_.toByte)
  }

  val decoder = new Decoder[String] {
    override def fromBytes(bytes: Array[Byte]) = new String(bytes.map(_.toChar))
  }

  val keyEncoder = new Encoder[Duration] {
    override def toBytes(t: Duration): Array[Byte] = BigInt(t.toMillis % numberOfTopics).toByteArray
  }

  val keyDecoder = new Decoder[Int] {
    override def fromBytes(bytes: Array[Byte]) = BigInt(bytes).toInt
  }
}

object worker {

  def process(event: DecodedEvent[Int, String]): Task[Unit] = Task.delay {
    Thread.sleep(250)
    println(
      s"thread=${Thread.currentThread.getName} topic=${event.messageAndMetadata.topic} " +
        s"partition=${event.messageAndMetadata.partition} key=${event.key} msg=${event.message}")
  }

}
