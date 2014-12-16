import org.scoverage.coveralls.CoverallsPlugin
import scoverage.ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages
import sbt._
import sbt.Keys._
import com.typesafe.sbt.pgp.PgpKeys._

object Finch extends Build {

  val baseSettings = Defaults.defaultSettings ++ Seq(
    libraryDependencies ++= Seq(
      "com.twitter" %% "finagle-httpx" % "6.24.0",
      "org.scalatest" %% "scalatest" % "2.2.1" % "test"
    ),
    scalacOptions ++= Seq( "-unchecked", "-deprecation", "-feature"),
    coverageExcludedPackages := ".*demo.*"
  )

  lazy val buildSettings = Seq(
    organization := "com.github.finagle",
    version := "0.4.0-SNAPSHOT",
    scalaVersion := "2.11.4",
    crossScalaVersions := Seq("2.10.4", "2.11.4")
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

  lazy val allSettings = baseSettings ++ buildSettings ++ publishSettings

  def defaultFinchProject(id: String, path: String, settings: Seq[sbt.Def.Setting[_]] = allSettings): Project =
    Project(
      id = id,
      base = file(path),
      settings = settings
    ) disablePlugins CoverallsPlugin

  lazy val root = Project(
    id = "finch",
    base = file("."),
    settings = allSettings
  ) aggregate(core, json, demo, jawn, argonaut)

  lazy val core = defaultFinchProject(id = "finch-core", path = "finch-core")

  lazy val json = defaultFinchProject(id = "finch-json", path = "finch-json") dependsOn core

  lazy val demo = defaultFinchProject(id = "finch-demo", path = "finch-demo") dependsOn(core, json)

  lazy val jawnSettings = allSettings ++ Seq(
    libraryDependencies ++= Seq(
      "org.spire-math" %% "jawn-parser" % "0.7.0",
      "org.spire-math" %% "jawn-ast" % "0.7.0"
    )
  )

  lazy val jawn = defaultFinchProject(
    id = "finch-jawn",
    path = "finch-jawn",
    settings = jawnSettings
  ) dependsOn core

  lazy val argonaut = defaultFinchProject(
    id = "finch-argonaut",
    path = "finch-argonaut",
    settings = allSettings ++ Seq(
      libraryDependencies += "io.argonaut" %% "argonaut" % "6.0.4"
    )
  ) dependsOn core
}
