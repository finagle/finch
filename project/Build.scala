import sbt._
import sbt.Keys._
import com.typesafe.sbt.pgp.PgpKeys._
import scoverage.ScoverageSbtPlugin.instrumentSettings
import CoverallsPlugin.coverallsSettings

object Finch extends Build {

  val baseSettings = Defaults.defaultSettings ++ Seq(
    libraryDependencies ++= Seq(
      "com.twitter" %% "finagle-http" % "6.22.0",
      "org.scalatest" % "scalatest_2.10" % "2.2.1" % "test"
    ),
    scalacOptions ++= Seq( "-unchecked", "-deprecation", "-feature")
  )

  lazy val buildSettings = Seq(
    organization := "com.github.finagle",
    version := "0.2.0",
    scalaVersion := "2.10.4"
  )

  lazy val publishSettings = Seq(
    publishMavenStyle := true,
    publishArtifact := true,
    useGpg := true,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    publishArtifact in Test := false,
    licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    homepage := Some(url("https://github.com/finagle/finch")),
    pomExtra := (
      <scm>
        <url>git://github.com/finagle/finch.git</url>
        <connection>scm:git://github.com/finagle/finch.git</connection>
      </scm>
      <developers>
        <developer>
          <id>vkostyukov</id>
          <name>Vladimir Kostyukov</name>
          <url>http://vkostyukov.ru</url>
        </developer>
      </developers>
    )
  )

  lazy val allSettings = baseSettings ++ buildSettings ++ publishSettings ++ instrumentSettings ++ coverallsSettings

  lazy val root = Project(
    id = "finch",
    base = file("."),
    settings = allSettings
  ) aggregate(core, json, demo, jawn)

  lazy val core = Project(
    id = "finch-core",
    base = file("finch-core"),
    settings = allSettings
  )

  lazy val json = Project(
    id = "finch-json",
    base = file("finch-json"),
    settings = allSettings
  ) dependsOn core

  lazy val demo = Project(
    id = "finch-demo",
    base = file("finch-demo"),
    settings = allSettings
  ) dependsOn(core, json)

  lazy val jawnSettings = allSettings ++ Seq(
    libraryDependencies ++= Seq(
      "org.spire-math" %% "jawn-parser" % "0.7.0",
      "org.spire-math" %% "jawn-ast" % "0.7.0"
    )
  )

  lazy val jawn = Project(
    id = "finch-jawn",
    base = file("finch-jawn"),
    settings = jawnSettings
  ) dependsOn core
}
