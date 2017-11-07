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
import sbt._
import Keys._

object Dependencies {
  val Kafka         = "org.apache.kafka"           %% "kafka"     % "0.8.2.2"
  val KafkaTest     = "org.apache.kafka"           %% "kafka"     % "0.8.2.2" classifier "test"
  val TreasureChest = "verizon.inf.treasure-chest" %% "core"      % "1.0.13"
  val Journal       = "com.verizon.journal"        %% "core"      % "2.2.0"
  val ScalaTest     = "org.scalatest"              %% "scalatest" % "2.2.6" % "test"
}
