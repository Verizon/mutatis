import verizon.build._
import Dependencies._

teamName in Global := Some("frontend")

projectName in Global := Some("xenomorph")

scalaVersion in Global := "2.11.8"

libraryDependencies ++= Seq(
  Kafka,
  TreasureChest,
  Journal
)

coverageHighlighting := true

// TODO remove this asap
coverageFailOnMinimum := false

