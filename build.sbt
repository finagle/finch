import sbtunidoc.Plugin.UnidocKeys._
import scoverage.ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages

lazy val buildSettings = Seq(
  organization := "com.github.finagle",
  version := "0.4.0",
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

val baseSettings = Defaults.defaultSettings ++ Seq(
  libraryDependencies ++= Seq(
    "com.twitter" %% "finagle-httpx" % "6.24.0",
    "org.scalatest" %% "scalatest" % "2.2.3" % "test"
  ),
  compilerOptions,
  coverageExcludedPackages := ".*demo.*"
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
  unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(`finch-demo`)
)

lazy val finch = project.in(file("."))
  .settings(allSettings: _*)
  .settings(docSettings: _*)
  .aggregate(`finch-core`, `finch-json`, `finch-demo`, `finch-jawn`, `finch-argonaut`, `finch-jackson`)

lazy val `finch-core` = project.in(file("core"))
  .settings(allSettings: _*)
  .disablePlugins(CoverallsPlugin)

lazy val `finch-json` = project.in(file("json"))
  .settings(allSettings: _*)
  .dependsOn(`finch-core`)
  .disablePlugins(CoverallsPlugin)

lazy val `finch-demo` = project.in(file("demo"))
  .settings(allSettings: _*)
  .dependsOn(`finch-core`, `finch-json`)
  .disablePlugins(CoverallsPlugin)

lazy val `finch-jawn` = project.in(file("jawn"))
  .settings(allSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.spire-math" %% "jawn-parser" % "0.7.2",
      "org.spire-math" %% "jawn-ast" % "0.7.2"
    )
  )
  .dependsOn(`finch-core`)
  .disablePlugins(CoverallsPlugin)

lazy val `finch-argonaut` = project.in(file("argonaut"))
  .settings(allSettings: _*)
  .settings(libraryDependencies += "io.argonaut" %% "argonaut" % "6.0.4")
  .dependsOn(`finch-core`)
  .disablePlugins(CoverallsPlugin)

lazy val `finch-jackson` = project.in(file("jackson"))
  .settings(allSettings: _*)
  .settings(libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.4.4")
  .dependsOn(`finch-core`)
  .disablePlugins(CoverallsPlugin)
