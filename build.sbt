import sbtunidoc.Plugin.UnidocKeys._
import scoverage.ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages

lazy val buildSettings = Seq(
  organization := "com.github.finagle",
  version := "0.7.0-SNAPSHOT",
  scalaVersion := "2.11.6",
  crossScalaVersions := Seq("2.10.5", "2.11.6")
)

lazy val compilerOptions = Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Xfuture"
)

val baseSettings = Seq(
  libraryDependencies ++= Seq(
    "com.chuusai" %% "shapeless" % "2.2.0-RC5",
    "com.twitter" %% "finagle-httpx" % "6.25.0",
    "org.scalacheck" %% "scalacheck" % "1.12.2" % "test",
    "org.scalatest" %% "scalatest" % "2.2.4" % "test",
    compilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full)
  ),
  scalacOptions ++= compilerOptions ++ (
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 11)) => Seq("-Ywarn-unused-import")
      case _ => Seq.empty
    }
  ),
  scalacOptions in (Compile, console) := compilerOptions,
  wartremoverWarnings in (Compile, compile) ++= Warts.allBut(Wart.NoNeedForMonad, Wart.Null, Wart.DefaultArguments)
)

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishArtifact := true,
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
  autoAPIMappings := true,
  apiURL := Some(url("https://finagle.github.io/finch/docs/")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/finagle/finch"),
      "scm:git:git@github.com:finagle/finch.git"
    )
  ),
  pomExtra :=
    <developers>
      <developer>
        <id>vkostyukov</id>
        <name>Vladimir Kostyukov</name>
        <url>http://vkostyukov.ru</url>
      </developer>
    </developers>
)

lazy val noPublish = Seq(
  publish := {},
  publishLocal := {}
)

lazy val allSettings = baseSettings ++ buildSettings ++ publishSettings

lazy val docSettings = site.settings ++ ghpages.settings ++ unidocSettings ++ Seq(
  site.addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), "docs"),
  git.remoteRepo := s"git@github.com:finagle/finch.git",
  unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(demo, playground)
)

lazy val root = project.in(file("."))
  .settings(moduleName := "finch")
  .settings(allSettings)
  .settings(docSettings)
  .settings(noPublish)
  .settings(
    initialCommands in console :=
      """
        |import io.finch.{Endpoint => _, _}
        |import io.finch.argonaut._
        |import io.finch.request._
        |import io.finch.request.items._
        |import io.finch.response._
        |import io.finch.route._
      """.stripMargin
  )
  .aggregate(core, json, demo, playground, jawn, argonaut, jackson, auth)
  .dependsOn(core, argonaut)

lazy val core = project
  .settings(moduleName := "finch-core")
  .settings(allSettings)
  .disablePlugins(CoverallsPlugin)

lazy val json = project
  .settings(moduleName := "finch-json")
  .settings(allSettings)
  .dependsOn(core)
  .disablePlugins(CoverallsPlugin)

lazy val demo = project
  .settings(moduleName := "finch-demo")
  .settings(allSettings)
  .settings(noPublish)
  .dependsOn(core, argonaut)
  .disablePlugins(CoverallsPlugin)

lazy val playground = project
  .settings(moduleName := "finch-playground")
  .settings(allSettings)
  .settings(noPublish)
  .settings(coverageExcludedPackages := "io\\.finch\\.playground\\..*")
  .dependsOn(core, jackson)
  .disablePlugins(CoverallsPlugin)

lazy val jawn = project
  .settings(moduleName := "finch-jawn")
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.spire-math" %% "jawn-parser" % "0.7.2",
      "org.spire-math" %% "jawn-ast" % "0.7.2"
    )
  )
  .dependsOn(core)
  .disablePlugins(CoverallsPlugin)

lazy val argonaut = project
  .settings(moduleName := "finch-argonaut")
  .settings(allSettings)
  .settings(libraryDependencies += "io.argonaut" %% "argonaut" % "6.1")
  .dependsOn(core)
  .disablePlugins(CoverallsPlugin)

lazy val jackson = project
  .settings(moduleName := "finch-jackson")
  .settings(allSettings)
  .settings(libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.5.2")
  .dependsOn(core)
  .disablePlugins(CoverallsPlugin)

lazy val auth = project
  .settings(moduleName := "finch-auth")
  .settings(allSettings)
  .dependsOn(core)
  .disablePlugins(CoverallsPlugin)
