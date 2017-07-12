import verizon.build._

teamName in Global := Some("frontend")

projectName in Global := Some("xenomorph")

scalaVersion in Global := "2.11.8"

//val xenomorph = project


import verizon.build._
import Dependencies._

libraryDependencies ++= Seq(
  Kafka,
  TreasureChest,
  Journal
)

coverageHighlighting := true

