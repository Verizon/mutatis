package xenomorph

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
