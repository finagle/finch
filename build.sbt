import sbtunidoc.Plugin.UnidocKeys._
import scoverage.ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages

lazy val buildSettings = Seq(
  organization := "com.github.finagle",
  version := "0.6.0-SNAPSHOT",
  scalaVersion := "2.11.5",
  crossScalaVersions := Seq("2.10.4", "2.11.5")
)

lazy val compilerOptions = scalacOptions ++= Seq(
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
) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, 11)) => Seq("-Ywarn-unused-import")
  case _ => Seq.empty
})

val baseSettings = Seq(
  libraryDependencies ++= Seq(
    "com.twitter" %% "finagle-httpx" % "6.24.0",
    "org.scalatest" %% "scalatest" % "2.2.3" % "test"
  ),
  compilerOptions,
  coverageExcludedPackages := ".*demo.*",
  wartremoverWarnings in (Compile, compile) ++= Warts.allBut(Wart.NoNeedForMonad, Wart.Null)
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

lazy val docSettings = site.settings ++ ghpages.settings ++ unidocSettings ++ Seq(
  site.addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), "docs"),
  git.remoteRepo := s"git@github.com:finagle/finch.git",
  unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(demo, playground)
)

lazy val root = project.in(file("."))
  .settings(moduleName := "finch")
  .settings(allSettings: _*)
  .settings(docSettings: _*)
  .aggregate(core, json, demo, playground, jawn, argonaut, jackson, auth)

lazy val core = project
  .settings(moduleName := "finch-core")
  .settings(allSettings: _*)
  .disablePlugins(CoverallsPlugin)

lazy val json = project
  .settings(moduleName := "finch-json")
  .settings(allSettings: _*)
  .dependsOn(core)
  .disablePlugins(CoverallsPlugin)

lazy val demo = project
  .settings(moduleName := "finch-demo")
  .settings(allSettings: _*)
  .dependsOn(core, json)
  .disablePlugins(CoverallsPlugin)

lazy val playground = project
  .settings(moduleName := "finch-playground")
  .settings(allSettings: _*)
  .dependsOn(core, jackson)
  .disablePlugins(CoverallsPlugin)

lazy val jawn = project
  .settings(moduleName := "finch-jawn")
  .settings(allSettings: _*)
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
  .settings(allSettings: _*)
  .settings(libraryDependencies += "io.argonaut" %% "argonaut" % "6.0.4")
  .dependsOn(core)
  .disablePlugins(CoverallsPlugin)

lazy val jackson = project
  .settings(moduleName := "finch-jackson")
  .settings(allSettings: _*)
  .settings(libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.4.4")
  .dependsOn(core)
  .disablePlugins(CoverallsPlugin)

lazy val auth = project
  .settings(moduleName := "finch-auth")
  .settings(allSettings: _*)
  .dependsOn(core)
  .disablePlugins(CoverallsPlugin)
