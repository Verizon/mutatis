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
