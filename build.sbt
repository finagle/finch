import ReleaseTransformations._
import microsites.ExtraMdFileConfig

lazy val buildSettings = Seq(
  organization := "com.github.finagle",
  scalaVersion := "3.0.2",
  crossScalaVersions := Seq("3.0.2", "2.13.3")
)

lazy val twitterVersion = "21.6.0"
lazy val circeVersion = "0.14.1"
lazy val circeFs2Version = "0.14.0"
lazy val shapelessVersion = "2.3.7"
lazy val catsVersion = "2.6.1"
lazy val argonautVersion = "6.3.6"
lazy val refinedVersion = "0.9.27"
lazy val catsEffectVersion = "2.5.3"
lazy val fs2Version = "2.5.9"

def compilerOptions(scalaVersion: String): Seq[String] = CrossVersion.partialVersion(scalaVersion) match {
  case Some((2, _)) =>
    Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-feature",
      "-language:existentials",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-unchecked",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen",
      "-Xlint",
      "-Wunused:imports"
    )
  case Some((3, _)) =>
    Seq("-Ykind-projector:underscores")
  case _ => Seq()
}

val testDependencies = Seq(
  "org.scalacheck" %% "scalacheck" % "1.15.4",
  "org.scalatest" %% "scalatest" % "3.2.9",
  "org.scalatestplus" %% "scalacheck-1-15" % "3.2.9.0",
  "org.typelevel" %% "cats-laws" % catsVersion,
  //"org.typelevel" %% "discipline-scalatest" % "2.0.1"
)

val baseSettings = Seq(
  libraryDependencies ++= Seq(
    "com.chuusai" %% "shapeless" % shapelessVersion cross CrossVersion.for3Use2_13,
    "org.typelevel" %% "cats-core" % catsVersion,
    "com.twitter" %% "finagle-http" % twitterVersion cross CrossVersion.for3Use2_13,
    "org.typelevel" %% "cats-effect" % catsEffectVersion
  ) ++ testDependencies.map(_ % "test"),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  ),
  scalacOptions ++= compilerOptions(scalaVersion.value),
  /*scalacOptions in(Compile, console) ~= {
    _.filterNot(Set("-Ywarn-unused-import"))
  },
  scalacOptions in(Compile, console) += "-Yrepl-class-based",*/
  Test / fork := true,
  ThisBuild / javaOptions ++= Seq("-Xss2048K"),
  //addCompilerPlugin("org.typelevel" % "kind-projector" % "0.10.3" cross CrossVersion.binary),
  ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.5.0",
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision
)

def updateVersionInFile(selectVersion: sbtrelease.Versions => String): ReleaseStep =
  ReleaseStep(action = st => {
    val newVersion = selectVersion(st.get(ReleaseKeys.versions).get)
    import scala.io.Source
    import java.io.PrintWriter

    // files containing version to update upon release
    val filesToUpdate = Seq(
      "docs/mdoc/index.md"
    )
    val pattern = """"com.github.finagle" %% "finch-.*" % "(.*)"""".r

    filesToUpdate.foreach { fileName =>
      val content = Source.fromFile(fileName).getLines.mkString("\n")
      val newContent =
        pattern.replaceAllIn(content,
          m => m.matched.replaceAllLiterally(m.subgroups.head, newVersion))
      new PrintWriter(fileName) {
        write(newContent);
        close()
      }
      val vcs = Project.extract(st).get(releaseVcs).get
      vcs.add(fileName).!
    }

    st
  })

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishArtifact := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  Test / publishArtifact := false,
  pgpSecretRing := file("local.secring.gpg"),
  pgpPublicRing := file("local.pubring.gpg"),
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseIgnoreUntrackedFiles := true,
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
  releaseVersionBump := sbtrelease.Version.Bump.Minor,
  releaseProcess := {
    Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      releaseStepCommandAndRemaining("+clean"),
      releaseStepCommandAndRemaining("+test"),
      setReleaseVersion,
      updateVersionInFile(_._1),
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("+publishSigned"),
      setNextVersion,
      commitNextVersion,
      releaseStepCommand("sonatypeReleaseAll"),
      pushChanges
    )
  },
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
      <developer>
        <id>sergeykolbasov</id>
        <name>Sergey Kolbasov</name>
        <url>https://twitter.com/sergey_kolbasov</url>
      </developer>
    </developers>
)

lazy val noPublish = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val allSettings = baseSettings ++ buildSettings ++ publishSettings

lazy val finch = project.in(file("."))
  .settings(moduleName := "finch")
  .settings(allSettings)
  .settings(noPublish)
  .settings(
    initialCommands / console :=
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
    "io.circe" %% "circe-generic" % circeVersion
  ))
  .aggregate(
    core, fs2, generic, argonaut, circe, benchmarks, test, jsonTest, examples, refined
  )
  .dependsOn(core, generic, circe)

lazy val core = project
  .settings(moduleName := "finchx-core")
  .settings(allSettings)

lazy val fs2 = project
  .settings(moduleName := "finchx-fs2")
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % fs2Version
    )
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val generic = project
  .settings(moduleName := "finchx-generic")
  .settings(allSettings)
  .dependsOn(core % "compile->compile;test->test")

lazy val test = project
  .settings(moduleName := "finchx-test")
  .settings(allSettings)
  .settings(coverageExcludedPackages := "io\\.finch\\.test\\..*")
  .settings(libraryDependencies ++= testDependencies)
  .dependsOn(core)

lazy val jsonTest = project.in(file("json-test"))
  .settings(moduleName := "finchx-json-test")
  .settings(allSettings)
  .settings(coverageExcludedPackages := "io\\.finch\\.test\\..*")
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-jawn" % circeVersion
    ) ++ testDependencies
  )
  .dependsOn(core)

lazy val argonaut = project
  .settings(moduleName := "finchx-argonaut")
  .settings(allSettings)
  .settings(libraryDependencies ++= Seq(
    "io.argonaut" %% "argonaut" % argonautVersion
  ))
  .dependsOn(core, jsonTest % "test")

lazy val circe = project
  .settings(moduleName := "finchx-circe")
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      //"io.circe" %% "circe-fs2" % circeFs2Version,
      "io.circe" %% "circe-jawn" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion % "test"
    )
  )
  .dependsOn(core, jsonTest % "test")

lazy val refined = project
  .settings(moduleName := "finchx-refined")
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "eu.timepit" %% "refined" % refinedVersion
    )
  )
  .dependsOn(core % "test->test;compile->compile")

lazy val examples = project
  .settings(moduleName := "finchx-examples")
  .settings(allSettings)
  .settings(noPublish)
  .settings(resolvers += "TM" at "https://maven.twttr.com")
  .settings(coverageExcludedPackages :=
    """
      |io\.finch\.div\..*;
      |io\.finch\.todo\..*;
      |io\.finch\.wrk\..*;
      |io\.finch\.iteratee\..*;
    """.stripMargin)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-generic" % circeVersion,
      "com.twitter" %% "finagle-stats" % twitterVersion cross CrossVersion.for3Use2_13,
      "com.twitter" %% "twitter-server" % twitterVersion cross CrossVersion.for3Use2_13
    )
  )
  .dependsOn(core, circe)

lazy val benchmarks = project
  .settings(moduleName := "finchx-benchmarks")
  .enablePlugins(JmhPlugin)
  .settings(allSettings)
  .settings(noPublish)
  .settings(libraryDependencies += "io.circe" %% "circe-generic" % circeVersion)
  .settings(coverageExcludedPackages := "io\\.finch\\..*;")
  .settings(
    run / javaOptions ++= Seq(
      "-Djava.net.preferIPv4Stack=true",
      "-XX:+AggressiveOpts",
      "-XX:+UseParNewGC",
      "-XX:+UseConcMarkSweepGC",
      "-XX:+CMSParallelRemarkEnabled",
      "-XX:+CMSClassUnloadingEnabled",
      "-XX:ReservedCodeCacheSize=128m",
      "-XX:MaxPermSize=1024m",
      "-XX:SurvivorRatio=128",
      "-XX:MaxTenuringThreshold=0",
      "-Xss8M",
      "-Xms512M",
      "-Xmx2G",
      "-server"
    )
  )
  .dependsOn(core, circe)

val validateCommands = List(
  "clean",
  "compile",
  "scalafix --check",
  "scalafmtCheckAll",
  "test:compile",
  "coverage",
  "test",
  "coverageReport",
  "coverageAggregate"
)
addCommandAlias("validate", validateCommands.mkString(";", ";", ""))
addCommandAlias("fmt", "all compile:scalafix; all test:scalafix; scalafmtAll")

