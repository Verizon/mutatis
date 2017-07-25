import verizon.build._
import Dependencies._

teamName in Global := Some("frontend")

projectName in Global := Some("xenomorph")

scalaVersion in Global := "2.11.8"

libraryDependencies ++= Seq(
  Kafka,
  KafkaTest,
  TreasureChest,
  Journal,
  ScalaTest
)

coverageHighlighting := true

parallelExecution in Test := false
