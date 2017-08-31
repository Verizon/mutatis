package common

import kafka.utils.{ZKGroupTopicDirs, ZkUtils}
import org.I0Itec.zkclient.ZkClient
import org.apache.zookeeper.data.Stat

object KafkaTestHelper {

  def offsetDir(group: String, topic: String) = new ZKGroupTopicDirs(group, topic).consumerOffsetDir

  def readZkNode(zkClient: ZkClient, path: String): (String, Stat) = ZkUtils.readData(zkClient, path)

  def getOffset(zkClient: ZkClient, group: String, topic: String, partition: Int) =
    readZkNode(zkClient, offsetDir(group, topic) + "/" + partition)._1

}
