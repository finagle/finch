import sbt._
import sbt.Keys._

object Finch extends Build {

  val baseSettings = Defaults.defaultSettings ++ Seq(
    libraryDependencies ++= Seq(
      "com.twitter" %% "finagle-http" % "6.14.0"
    )
  )

  lazy val buildSettings = Seq(
    organization := "io",
    version := "0.0.15",
    scalaVersion := "2.10.3"
  )

  lazy val publishSettings = Seq(
    publishMavenStyle := true,
    publishArtifact := true,
    publishTo := Some(Resolver.file("localDirectory", file(Path.userHome.absolutePath + "/repo"))),
    licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    homepage := Some(url("https://github.com/vkostyukov/finch")),
    pomExtra := (
      <scm>
        <url>git://github.com/vkostyukov/finch.git</url>
        <connection>scm:git://github.com/vkostyukov/finch.git</connection>
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

  lazy val root = Project(id = "finch",
    base = file("."),
    settings = baseSettings ++ buildSettings ++ publishSettings)
}
