import sbt._
import Keys._

object Dependencies {
  val Kafka         = "org.apache.kafka"           %% "kafka" % "0.8.2.2"
  val TreasureChest = "verizon.inf.treasure-chest" %% "core"  % "1.0.13"
  val Journal       = "com.verizon.journal"        %% "core"  % "2.2.0"
}
