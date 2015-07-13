import sbtunidoc.Plugin.UnidocKeys._
import scoverage.ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages

lazy val buildSettings = Seq(
  organization := "com.github.finagle",
  version := "0.8.0-SNAPSHOT",
  scalaVersion := "2.11.7",
  crossScalaVersions := Seq("2.10.5", "2.11.7")
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
  "-Xfuture",
  "-Xlint"
)

val testDependencies = Seq(
  "org.scalacheck" %% "scalacheck" % "1.12.4",
  "org.scalatest" %% "scalatest" % "2.2.5"
)

val baseSettings = Seq(
  libraryDependencies ++= Seq(
    "com.chuusai" %% "shapeless" % "2.2.3",
    "com.twitter" %% "finagle-httpx" % "6.26.0",
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    compilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full)
  ) ++ testDependencies.map(_ % "test"),
  scalacOptions ++= compilerOptions ++ (
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 11)) => Seq("-Ywarn-unused-import")
      case _ => Seq.empty
    }
  ),
  scalacOptions in (Compile, console) := compilerOptions :+ "-Yrepl-class-based",
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
      <developer>
        <id>travisbrown</id>
        <name>Travis Brown</name>
        <url>https://meta.plasm.us/</url>
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
  unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(demo)
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
  .aggregate(core, demo, argonaut, jackson, json4s, auth, benchmarks, petstore)
  .dependsOn(core, argonaut)

lazy val core = project
  .settings(moduleName := "finch-core")
  .settings(allSettings)
  .settings(coverageExcludedPackages := "io\\.finch\\.micro\\..*")

lazy val test = project
  .settings(moduleName := "finch-test")
  .settings(allSettings)
  .settings(coverageExcludedPackages := "io\\.finch\\.test\\..*")
  .settings(
    libraryDependencies ++= "io.argonaut" %% "argonaut" % "6.1" +: testDependencies
  )
  .dependsOn(core)

lazy val demo = project
  .settings(moduleName := "finch-demo")
  .configs(IntegrationTest.extend(Test))
  .settings(allSettings)
  .settings(noPublish)
  .settings(Defaults.itSettings)
  .settings(parallelExecution in IntegrationTest := false)
  .disablePlugins(JmhPlugin)
  .dependsOn(core, argonaut, test % "test,it")

lazy val petstore = project
    .settings(moduleName := "finch-petstore")
    .configs(IntegrationTest.extend(Test))
    .settings(allSettings)
    .settings(noPublish)
    .settings(Defaults.itSettings)
    .settings(parallelExecution in IntegrationTest := false)
    .settings(coverageExcludedPackages := "io\\.finch\\.petstore\\.PetstoreApp.*")
    .disablePlugins(JmhPlugin)
    .dependsOn(core, argonaut, test % "test,it")

lazy val argonaut = project
  .settings(moduleName := "finch-argonaut")
  .settings(allSettings)
  .settings(libraryDependencies += "io.argonaut" %% "argonaut" % "6.1")
  .dependsOn(core, test % "test")

lazy val jackson = project
  .settings(moduleName := "finch-jackson")
  .settings(allSettings)
  .settings(libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.5.3")
  .dependsOn(core, test % "test")

lazy val json4s = project
  .settings(moduleName := "finch-json4s")
  .settings(allSettings)
  .settings(libraryDependencies ++= Seq(
    "org.json4s" %% "json4s-jackson" % "3.2.11",
    "org.json4s" %% "json4s-ext" % "3.2.11")
  )
  .dependsOn(core, test % "test")

lazy val auth = project
  .settings(moduleName := "finch-auth")
  .settings(allSettings)
  .dependsOn(core)

lazy val benchmarks = project
  .settings(moduleName := "finch-benchmarks")
  .settings(allSettings)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(parallelExecution in IntegrationTest := false)
  .settings(coverageExcludedPackages := "io\\.finch\\.benchmarks\\.service\\.UserServiceBenchmark")
  .settings(
    javaOptions in run ++= Seq(
      "-Djava.net.preferIPv4Stack=true",
      "-XX:+AggressiveOpts",
      "-XX:+UseParNewGC",
      "-XX:+UseConcMarkSweepGC",
      "-XX:+CMSParallelRemarkEnabled",
      "-XX:+CMSClassUnloadingEnabled",
      "-XX:ReservedCodeCacheSize=128m",
      "-XX:MaxPermSize=1024m",
      "-Xss8M",
      "-Xms512M",
      "-XX:SurvivorRatio=128",
      "-XX:MaxTenuringThreshold=0",
      "-Xss8M",
      "-Xms512M",
      "-Xmx2G",
      "-server"
    )
  )
  .dependsOn(core, argonaut, jackson, json4s, test % "it")
