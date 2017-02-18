import sbtunidoc.Plugin.UnidocKeys._

lazy val buildSettings = Seq(
  organization := "com.github.finagle",
  version := "0.13.1",
  scalaVersion := "2.12.1",
  crossScalaVersions := Seq("2.11.8", "2.12.1")
)

lazy val finagleVersion = "6.42.0"
lazy val utilVersion = "6.41.0"
lazy val twitterServerVersion = "1.27.0"
lazy val finagleOAuth2Version = "0.4.0"
lazy val circeVersion = "0.7.0"
lazy val circeJacksonVersion = "0.7.0"
lazy val catbirdVersion = "0.12.0"
lazy val shapelessVersion = "2.3.2"
lazy val catsVersion = "0.9.0"
lazy val sprayVersion = "1.3.3"
lazy val playVersion = "2.6.0-M1"
lazy val jacksonVersion = "2.8.6"
lazy val argonautVersion = "6.2-RC2"
lazy val json4sVersion = "3.5.0"

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
  "org.scalacheck" %% "scalacheck" % "1.13.4",
  "org.scalatest" %% "scalatest" % "3.0.1",
  "org.typelevel" %% "cats-laws" % catsVersion,
  "org.typelevel" %% "discipline" % "0.7.3"
)

val baseSettings = Seq(
  libraryDependencies ++= Seq(
    "com.chuusai" %% "shapeless" % shapelessVersion,
    "org.typelevel" %% "cats-core" % catsVersion,
    "com.twitter" %% "finagle-http" % finagleVersion,
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "io.catbird" %% "catbird-util" % catbirdVersion
  ) ++ testDependencies.map(_ % "test"),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  ),
  scalacOptions ++= compilerOptions ++ Seq("-Ywarn-unused-import"),
  scalacOptions in (Compile, console) ~= (_ filterNot (_ == "-Ywarn-unused-import")),
  scalacOptions in (Compile, console) += "-Yrepl-class-based"
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
        <url>http://vkostyukov.net</url>
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
  publishLocal := {},
  publishArtifact := false
)

lazy val allSettings = baseSettings ++ buildSettings ++ publishSettings

lazy val docSettings = site.settings ++ ghpages.settings ++ unidocSettings ++ Seq(
  site.addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), "docs"),
  git.remoteRepo := s"git@github.com:finagle/finch.git",
  unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(benchmarks, jsonTest)
)

lazy val finch = project.in(file("."))
  .settings(moduleName := "finch")
  .settings(allSettings)
  .settings(docSettings)
  .settings(noPublish)
  .settings(
    initialCommands in console :=
      """
        |import io.finch._
        |import io.finch.circe._
        |import io.finch.items._
        |import com.twitter.util.{Future, Await}
        |import com.twitter.concurrent.AsyncStream
        |import com.twitter.io.{Buf, Reader}
        |import com.twitter.finagle.Service
        |import com.twitter.finagle.Http
        |import com.twitter.finagle.http.{Request, Response, Status, Version}
        |import io.circe._
        |import io.circe.generic.auto._
        |import shapeless._
      """.stripMargin
  )
  .settings(libraryDependencies ++= Seq(
    "io.circe" %% "circe-generic" % circeVersion,
    "io.spray" %%  "spray-json" % sprayVersion
  ))
  .aggregate(
    core, argonaut, jackson, json4s, circe, playjson, sprayjson, benchmarks, test, jsonTest, oauth2,
    examples, sse
  )
  .dependsOn(core, circe)

lazy val core = project
  .settings(moduleName := "finch-core")
  .settings(allSettings)

lazy val test = project
  .settings(moduleName := "finch-test")
  .settings(allSettings)
  .settings(coverageExcludedPackages := "io\\.finch\\.test\\..*")
  .settings(libraryDependencies ++= testDependencies)
  .dependsOn(core)

lazy val jsonTest = project.in(file("json-test"))
  .settings(moduleName := "finch-json-test")
  .settings(allSettings)
  .settings(noPublish)
  .settings(coverageExcludedPackages := "io\\.finch\\.test\\..*")
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-jawn" % circeVersion
    ) ++ testDependencies
  )
  .dependsOn(core)

lazy val argonaut = project
  .settings(moduleName := "finch-argonaut")
  .settings(allSettings)
  .settings(libraryDependencies ++= Seq(
    "io.argonaut" %% "argonaut" % argonautVersion,
    "io.argonaut" %% "argonaut-jawn" % argonautVersion
  ))
  .dependsOn(core, jsonTest % "test")

lazy val jackson = project
  .settings(moduleName := "finch-jackson")
  .settings(allSettings)
  .settings(libraryDependencies ++= Seq(
    "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion
  ))
  .dependsOn(core, jsonTest % "test")

lazy val json4s = project
  .settings(moduleName := "finch-json4s")
  .settings(allSettings)
  .settings(libraryDependencies ++= Seq(
    "org.json4s" %% "json4s-jackson" % json4sVersion,
    "org.json4s" %% "json4s-ext" % json4sVersion
  ))
  .dependsOn(core, jsonTest % "test")

lazy val circe = project
  .settings(moduleName := "finch-circe")
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-jawn" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion % "test",
      "io.circe" %% "circe-jackson28" % circeJacksonVersion
    )
  )
  .dependsOn(core, jsonTest % "test")

lazy val playjson = project
  .settings(moduleName :="finch-playjson")
  .settings(allSettings)
  .settings(libraryDependencies += "com.typesafe.play" %% "play-json" % playVersion)
  .dependsOn(core, jsonTest % "test")

lazy val sprayjson = project
  .settings(moduleName := "finch-sprayjson")
  .settings(allSettings)
  .settings(libraryDependencies += "io.spray" %%  "spray-json" % sprayVersion)
  .dependsOn(core, jsonTest % "test")

lazy val oauth2 = project
  .settings(moduleName := "finch-oauth2")
  .settings(allSettings)
  .settings(libraryDependencies ++= Seq(
    "com.github.finagle" %% "finagle-oauth2" % finagleOAuth2Version,
    "commons-codec" % "commons-codec" % "1.9",
    "org.mockito" % "mockito-all" % "1.10.19" % "test"
  ))
  .dependsOn(core)

lazy val sse = project
  .settings(moduleName := "finch-sse")
  .settings(allSettings)
  .dependsOn(core)

lazy val examples = project
  .settings(moduleName := "finch-examples")
  .settings(allSettings)
  .settings(noPublish)
  .settings(resolvers += "TM" at "http://maven.twttr.com")
  .settings(coverageExcludedPackages :=
    """
      |io\.finch\.div\..*;
      |io\.finch\.todo\..*;
      |io\.finch\.eval\..*;
      |io\.finch\.streaming\..*;
      |io\.finch\.oauth2\..*;
      |io\.finch\.wrk\..*;
      |io\.finch\.sse\..*;
    """.stripMargin)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-generic" % circeVersion,
      "com.twitter" %% "twitter-server" % twitterServerVersion,
      "com.twitter" %% "util-eval" % utilVersion,
      "com.github.finagle" %% "finagle-oauth2" % finagleOAuth2Version
    )
  )
  .dependsOn(core, circe, jackson, oauth2)

lazy val benchmarks = project
  .settings(moduleName := "finch-benchmarks")
  .enablePlugins(JmhPlugin)
  .settings(allSettings)
  .settings(noPublish)
  .settings(libraryDependencies += "io.circe" %% "circe-generic" % circeVersion)
  .settings(coverageExcludedPackages := "io\\.finch\\..*;")
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
  .dependsOn(core, circe, jackson)

val validateCommands = List(
  "clean",
  "scalastyle",
  "test:scalastyle",
  "compile",
  "test:compile",
  "coverage",
  "test",
  "coverageReport",
  "coverageAggregate"
)
addCommandAlias("validate", validateCommands.mkString(";", ";", ""))
