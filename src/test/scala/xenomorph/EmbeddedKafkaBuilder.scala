/*
*****************************************
Copyright 2017  Verizon.
               
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
****************************************************************
*/
package mutatis

import java.util.UUID

import kafka.admin.TopicCommand
import kafka.consumer.ConsumerConfig
import kafka.producer._
import kafka.server._
import org.I0Itec.zkclient.ZkClient
import org.scalatest._
import kafka.utils._
import kafka.zk._

trait EmbeddedKafkaBuilder extends BeforeAndAfterEach { this: Suite =>
  val brokerId  = 0
  val topic     = "topic"
  val zkConnect = TestZKUtils.zookeeperConnect

  var kafkaServer: KafkaServer       = _
  var zkClient: ZkClient             = _
  var zkServer: EmbeddedZookeeper    = _
  var consumerConfig: ConsumerConfig = _
  var producerConfig: ProducerConfig = _
  val groupId = UUID.randomUUID().toString

  override def beforeEach(): Unit = {
    // setup Zookeeper
    zkServer = new EmbeddedZookeeper(zkConnect)
    zkClient = new ZkClient(zkServer.connectString, 30000, 30000, ZKStringSerializer)

    // setup Broker
    val port  = TestUtils.choosePort
    val props = TestUtils.createBrokerConfig(brokerId, port, enableControlledShutdown = true)

    val config = new KafkaConfig(props)
    val mock   = new MockTime()
    kafkaServer = TestUtils.createServer(config, mock)

    // create topic
    val arguments = Array("--topic", topic, "--partitions", "1", "--replication-factor", "1")
    TopicCommand.createTopic(zkClient, new TopicCommand.TopicCommandOptions(arguments))

    TestUtils.waitUntilMetadataIsPropagated(List(kafkaServer), topic, 0, 5000)

    val consumerProperties =
      TestUtils.createConsumerProperties(
        zkConnect = zkServer.connectString,
        groupId = groupId,
        consumerId = UUID.randomUUID().toString,
        consumerTimeout = -1)

    consumerConfig = new ConsumerConfig(consumerProperties)

    // setup producer
    val properties = TestUtils.getProducerConfig("localhost:" + port)
    producerConfig = new ProducerConfig(properties)

    super.beforeEach() // To be stackable, must call super.beforeEach
  }

  override def afterEach(): Unit = {
    try super.afterEach() // To be stackable, must call super.afterEach
    finally {
      kafkaServer.shutdown()
      zkClient.close()
      zkServer.shutdown()
    }
  }
}
