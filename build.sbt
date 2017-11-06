import microsites.ExtraMdFileConfig

lazy val buildSettings = Seq(
  organization := "com.github.finagle",
  version := "0.16.0-M3",
  scalaVersion := "2.12.3",
  crossScalaVersions := Seq("2.11.11", "2.12.3")
)

lazy val finagleVersion = "17.10.0"
lazy val twitterServerVersion = "1.32.0"
lazy val circeVersion = "0.9.0-M1"
lazy val circeJacksonVersion = "0.9.0-M1"
lazy val catbirdVersion = "0.18.0"
lazy val shapelessVersion = "2.3.2"
lazy val catsVersion = "1.0.0-MF"
lazy val sprayVersion = "1.3.3"
lazy val playVersion = "2.6.6"
lazy val jacksonVersion = "2.8.8"
lazy val argonautVersion = "6.2"
lazy val json4sVersion = "3.5.2"
lazy val iterateeVersion = "0.13.0"

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
  "org.scalacheck" %% "scalacheck" % "1.13.5",
  "org.scalatest" %% "scalatest" % "3.0.4",
  "org.typelevel" %% "cats-laws" % catsVersion,
  "org.typelevel" %% "discipline" % "0.8"
)

val baseSettings = Seq(
  libraryDependencies ++= Seq(
    "com.chuusai" %% "shapeless" % shapelessVersion,
    "org.typelevel" %% "cats-core" % catsVersion,
    "com.twitter" %% "finagle-http" % finagleVersion,
    scalaOrganization.value % "scala-reflect" % scalaVersion.value,
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
      <developer>
        <id>rpless</id>
        <name>Ryan Plessner</name>
        <url>https://twitter.com/ryan_plessner</url>
      </developer>

    </developers>
)

lazy val noPublish = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val allSettings = baseSettings ++ buildSettings ++ publishSettings

lazy val docSettings = allSettings ++ Seq(
  micrositeName := "Finch",
  micrositeDescription := "Scala combinator library for building Finagle HTTP services",
  micrositeAuthor := "Vladimir Kostyukov",
  micrositeHighlightTheme := "atom-one-light",
  micrositeHomepage := "https://finagle.github.io/finch/",
  micrositeDocumentationUrl := "api",
  micrositeGithubOwner := "finagle",
  micrositeGithubRepo := "finch",
  micrositeBaseUrl := "finch",
  micrositeExtraMdFiles := Map(file("CONTRIBUTING.md") -> ExtraMdFileConfig("contributing.md", "docs")),
  micrositePalette := Map(
    "brand-primary" -> "#3b3c3b",
    "brand-secondary" -> "#4c4d4c",
    "brand-tertiary" -> "#5d5e5d",
    "gray-dark" -> "#48494B",
    "gray" -> "#7D7E7D",
    "gray-light" -> "#E5E6E5",
    "gray-lighter" -> "#F4F3F4",
    "white-color" -> "#FFFFFF"),
  addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), micrositeDocumentationUrl),
  ghpagesNoJekyll := false,
  scalacOptions in (ScalaUnidoc, unidoc) ++= Seq(
    "-groups",
    "-implicits",
    "-skip-packages", "scalaz",
    "-doc-source-url", scmInfo.value.get.browseUrl + "/tree/master€{FILE_PATH}.scala",
    "-sourcepath", baseDirectory.in(LocalRootProject).value.getAbsolutePath,
    "-doc-root-content", (resourceDirectory.in(Compile).value / "rootdoc.txt").getAbsolutePath
  ),
  scalacOptions ~= {
    _.filterNot(Set("-Yno-predef", "-Xlint"))
  },
  git.remoteRepo := "git@github.com:finagle/finch.git",
  unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(benchmarks, jsonTest),
  includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.svg" | "*.js" | "*.swf" | "*.yml" | "*.md",
  siteSubdirName in ScalaUnidoc := "docs"
)

lazy val finch = project.in(file("."))
  .settings(moduleName := "finch")
  .settings(allSettings)
  .settings(noPublish)
  .settings(
    initialCommands in console :=
      """
        |import io.finch._
        |import io.finch.circe._
        |import io.finch.generic._
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
    core, iteratee, generic, argonaut, jackson, json4s, circe, playjson, sprayjson, benchmarks, test, jsonTest, examples, sse
  )
  .dependsOn(core, iteratee, generic, circe)

lazy val core = project
  .settings(moduleName := "finch-core")
  .settings(allSettings)

lazy val iteratee = project
  .settings(moduleName := "finch-iteratee")
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.iteratee" %% "iteratee-core" % iterateeVersion,
      "io.iteratee" %% "iteratee-twitter" % iterateeVersion
    )
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val generic = project
  .settings(moduleName := "finch-generic")
  .settings(allSettings)
  .dependsOn(core % "compile->compile;test->test")

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
      "io.circe" %% "circe-jawn" % circeVersion,
      "io.circe" %% "circe-streaming" % circeVersion
    ) ++ testDependencies
  )
  .dependsOn(core, iteratee)

lazy val argonaut = project
  .settings(moduleName := "finch-argonaut")
  .settings(allSettings)
  .settings(libraryDependencies ++= Seq(
    "io.argonaut" %% "argonaut" % argonautVersion
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
      "io.circe" %% "circe-streaming" % circeVersion,
      "io.circe" %% "circe-jawn" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion % "test",
      "io.circe" %% "circe-jackson28" % circeJacksonVersion
    )
  )
  .dependsOn(core, iteratee, jsonTest % "test")

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

lazy val sse = project
  .settings(moduleName := "finch-sse")
  .settings(allSettings)
  .dependsOn(core)

lazy val docs = project
  .settings(moduleName := "finch-docs")
  .settings(docSettings)
  .settings(noPublish)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-generic" % circeVersion,
      "com.twitter" %% "twitter-server" % twitterServerVersion,
      "joda-time" % "joda-time" % "2.9.9",
      "org.mockito" % "mockito-all" % "1.10.19"
    )
  )
  .enablePlugins(MicrositesPlugin, ScalaUnidocPlugin)
  .dependsOn(core, circe, jackson, sse, argonaut, json4s, playjson, iteratee)


lazy val examples = project
  .settings(moduleName := "finch-examples")
  .settings(allSettings)
  .settings(noPublish)
  .settings(resolvers += "TM" at "http://maven.twttr.com")
  .settings(coverageExcludedPackages :=
    """
      |io\.finch\.div\..*;
      |io\.finch\.todo\..*;
      |io\.finch\.streaming\..*;
      |io\.finch\.wrk\..*;
      |io\.finch\.sse\..*;
    """.stripMargin)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-generic" % circeVersion,
      "com.twitter" %% "finagle-stats" % finagleVersion,
      "com.twitter" %% "twitter-server" % twitterServerVersion
    )
  )
  .dependsOn(core, circe, jackson, iteratee)

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
