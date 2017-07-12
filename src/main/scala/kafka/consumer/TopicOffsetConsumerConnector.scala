package kafka.consumer

import kafka.common.TopicAndPartition

class TopicOffsetConsumerConnector(consumerConfig: ConsumerConfig) {
  private val zkConnector                  = new ZookeeperConsumerConnector(consumerConfig, true)
  val consumerConnector: ConsumerConnector = zkConnector

  def commitOffset(topicPartition: TopicAndPartition, offset: Long) =
    zkConnector.commitOffsetToZooKeeper(topicPartition, offset)
}
