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
package verizon.build

import sbt._, Keys._
import xerial.sbt.Sonatype.autoImport.sonatypeProfileName

object CentralRequirementsPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = RigPlugin

  override lazy val projectSettings = Seq(
    publishTo := Some("releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"),
    sonatypeProfileName := "io.verizon",
    pomExtra in Global := {
      <developers>
        <developer>
          <id>fadeddata</id>
          <name>Dustin Withers</name>
          <url>https://github.com/fadeddata</url>
        </developer>
        <developer>
          <id>haripriyamurthy</id>
          <name>Haripriya Murthy</name>
          <url>https://github.com/haripriyamurthy</url>
        </developer>
        <developer>
          <id>kothari-pk</id>
          <name>Prateek Kothari</name>
          <url>https://github.com/kothari-pk</url>
        </developer>
        <developer>
          <id>rolandomanrique</id>
          <name>Rolando Manrique</name>
          <url>https://github.com/rolandomanrique</url>
        </developer>
        <developer>
          <id>dougkang</id>
          <name>Doug Kang</name>
          <url>https://github.com/dougkang</url>
        </developer>
      </developers>
    },
    licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    homepage := Some(url("http://verizon.github.io/mutatis/")),
    scmInfo := Some(ScmInfo(url("https://github.com/verizon/mutatis"),
                                "git@github.com:verizon/mutatis.git"))
  )
}