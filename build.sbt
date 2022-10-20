import ReleaseTransformations._
import microsites.ExtraMdFileConfig

lazy val buildSettings = Seq(
  organization := "com.github.finagle",
  scalaVersion := "2.13.8",
  crossScalaVersions := Seq("2.12.15", "2.13.8")
)

lazy val twitterVersion = "22.7.0"
lazy val circeVersion = "0.14.2"
lazy val circeFs2Version = "0.14.0"
lazy val circeIterateeVersion = "0.13.0-M2"
lazy val shapelessVersion = "2.3.10"
lazy val catsVersion = "2.8.0"
lazy val argonautVersion = "6.3.8"
lazy val iterateeVersion = "0.20.0"
lazy val refinedVersion = "0.10.1"
lazy val catsEffectVersion = "3.3.14"
lazy val fs2Version = "3.3.0"

def compilerOptions(scalaVersion: String): Seq[String] = Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Xlint",
  "-Xfatal-warnings"
) ++ (CrossVersion.partialVersion(scalaVersion) match {
  case Some((2, scalaMajor)) if scalaMajor == 12 => scala212CompilerOptions
  case Some((2, scalaMajor)) if scalaMajor == 13 => scala213CompilerOptions
})

lazy val scala212CompilerOptions = Seq(
  "-Yno-adapted-args",
  "-Ywarn-unused-import",
  "-Xfuture"
)

lazy val scala213CompilerOptions = Seq(
  "-Wunused:imports",
  "-Xlint:-byname-implicit"
)

val testDependencies = Seq(
  "org.scalacheck" %% "scalacheck" % "1.17.0",
  "org.scalatest" %% "scalatest" % "3.2.14",
  "org.typelevel" %% "cats-laws" % catsVersion,
  "org.typelevel" %% "discipline-scalatest" % "2.2.0"
)

val formatOnCompile = System.getenv("CI") != "true"

val baseSettings = Seq(
  libraryDependencies ++= Seq(
    "com.chuusai" %% "shapeless" % shapelessVersion,
    "org.typelevel" %% "cats-core" % catsVersion,
    "com.twitter" %% "finagle-http" % twitterVersion,
    scalaOrganization.value % "scala-reflect" % scalaVersion.value,
    "org.typelevel" %% "cats-effect" % catsEffectVersion
  ) ++ testDependencies.map(_ % "test"),
  resolvers ++= Resolver.sonatypeOssRepos("releases"),
  resolvers ++= Resolver.sonatypeOssRepos("snapshots"),
  scalacOptions ++= compilerOptions(scalaVersion.value),
  (Compile / console / scalacOptions) ~= (_.filterNot(Set("-Ywarn-unused-import"))),
  (Compile / console / scalacOptions) += "-Yrepl-class-based",
  (Test / fork) := true,
  (ThisBuild / javaOptions) ++= Seq("-Xss2048K"),
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full),
  ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0",
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
  scalafixOnCompile := formatOnCompile,
  scalafmtOnCompile := formatOnCompile
)

def updateVersionInFile(selectVersion: sbtrelease.Versions => String): ReleaseStep =
  ReleaseStep(action = st => {
    import scala.io.Source
    import java.io.PrintWriter

    val newVersion = selectVersion(st.get(ReleaseKeys.versions).get)
    // files containing version to update upon release
    val filesToUpdate = Seq("docs/mdoc/index.md")
    val pattern = """"com.github.finagle" %% "finch-.*" % "(.*)"""".r

    filesToUpdate.foreach { fileName =>
      val source = Source.fromFile(fileName)
      val content =
        try source.mkString
        finally source.close()
      val newContent =
        pattern.replaceAllIn(content, m => m.matched.replaceAllLiterally(m.subgroups.head, newVersion))
      val writer = new PrintWriter(fileName)
      try writer.write(newContent)
      finally writer.close()
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
  (Test / publishArtifact) := false,
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

lazy val docSettings = allSettings ++ Seq(
  micrositeName := "Finch",
  micrositeDescription := "Scala combinator library for building Finagle HTTP services",
  micrositeAuthor := "Vladimir Kostyukov",
  mdocIn := baseDirectory.value / "mdoc",
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
    "white-color" -> "#FFFFFF"
  ),
  addMappingsToSiteDir(ScalaUnidoc / packageDoc / mappings, micrositeDocumentationUrl),
  ghpagesNoJekyll := false,
  (ScalaUnidoc / unidoc / scalacOptions) ++= Seq(
    "-groups",
    "-implicits",
    "-skip-packages",
    "scalaz",
    "-doc-source-url",
    scmInfo.value.get.browseUrl + "/tree/master${FILE_PATH}.scala",
    "-sourcepath",
    (LocalRootProject / baseDirectory).value.getAbsolutePath,
    "-doc-root-content",
    ((Compile / resourceDirectory).value / "rootdoc.txt").getAbsolutePath
  ),
  scalacOptions ~= (_.filterNot(Set("-Yno-predef", "-Xlint", "-Ywarn-unused-import"))),
  git.remoteRepo := "git@github.com:finagle/finch.git",
  (ScalaUnidoc / unidoc / unidocProjectFilter) := inAnyProject -- inProjects(benchmarks, jsonTest),
  (makeSite / includeFilter) := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.svg" | "*.js" | "*.swf" | "*.yml" | "*.md",
  (ScalaUnidoc / siteSubdirName) := "docs",
  (Global / excludeLintKeys) += siteSubdirName
)

lazy val finch = project
  .in(file("."))
  .settings(moduleName := "finch")
  .settings(allSettings)
  .settings(noPublish)
  .settings(
    (console / initialCommands) :=
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
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-generic" % circeVersion
    )
  )
  .aggregate(
    core,
    fs2,
    iteratee,
    generic,
    argonaut,
    circe,
    benchmarks,
    test,
    jsonTest,
    examples,
    refined
  )
  .dependsOn(core, iteratee, generic, circe)

lazy val core = project.settings(moduleName := "finch-core").settings(allSettings)

lazy val iteratee = project
  .settings(moduleName := "finch-iteratee")
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.iteratee" %% "iteratee-core" % iterateeVersion
    )
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val fs2 = project
  .settings(moduleName := "finch-fs2")
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % fs2Version
    )
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val generic = project.settings(moduleName := "finch-generic").settings(allSettings).dependsOn(core % "compile->compile;test->test")

lazy val test = project
  .settings(moduleName := "finch-test")
  .settings(allSettings)
  .settings(coverageExcludedPackages := "io\\.finch\\.test\\..*")
  .settings(libraryDependencies ++= testDependencies)
  .dependsOn(core)

lazy val jsonTest = project
  .in(file("json-test"))
  .settings(moduleName := "finch-json-test")
  .settings(allSettings)
  .settings(coverageExcludedPackages := "io\\.finch\\.test\\..*")
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-jawn" % circeVersion,
      "io.circe" %% "circe-iteratee" % circeIterateeVersion
    ) ++ testDependencies
  )
  .dependsOn(core, iteratee)

lazy val argonaut = project
  .settings(moduleName := "finch-argonaut")
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.argonaut" %% "argonaut" % argonautVersion
    )
  )
  .dependsOn(core, jsonTest % "test")

lazy val circe = project
  .settings(moduleName := "finch-circe")
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-iteratee" % circeIterateeVersion,
      "io.circe" %% "circe-fs2" % circeFs2Version,
      "io.circe" %% "circe-jawn" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion % "test"
    )
  )
  .dependsOn(core, jsonTest % "test")

lazy val refined = project
  .settings(moduleName := "finch-refined")
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "eu.timepit" %% "refined" % refinedVersion,
      "eu.timepit" %% "refined-cats" % refinedVersion % "test",
      "eu.timepit" %% "refined-scalacheck" % refinedVersion % "test"
    )
  )
  .dependsOn(core % "test->test;compile->compile")

lazy val docs = project
  .settings(moduleName := "finch-docs")
  .settings(docSettings)
  .settings(noPublish)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-generic" % circeVersion,
      "com.twitter" %% "twitter-server" % twitterVersion,
      "joda-time" % "joda-time" % "2.10.14",
      "org.mockito" % "mockito-all" % "1.10.19"
    )
  )
  .enablePlugins(MicrositesPlugin, ScalaUnidocPlugin)
  .dependsOn(core, circe, argonaut, iteratee, refined, fs2)

lazy val examples = project
  .settings(moduleName := "finch-examples")
  .settings(allSettings)
  .settings(noPublish)
  .settings(resolvers += "TM" at "https://maven.twttr.com")
  .settings(
    coverageExcludedPackages :=
      """
        |io\.finch\.div\..*;
        |io\.finch\.todo\..*;
        |io\.finch\.wrk\..*;
        |io\.finch\.iteratee\..*;
      """.stripMargin
  )
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-generic" % circeVersion,
      "com.twitter" %% "finagle-stats" % twitterVersion,
      "com.twitter" %% "twitter-server" % twitterVersion
    )
  )
  .dependsOn(core, circe, iteratee)

lazy val benchmarks = project
  .settings(moduleName := "finch-benchmarks")
  .enablePlugins(JmhPlugin)
  .settings(allSettings)
  .settings(noPublish)
  .settings(libraryDependencies += "io.circe" %% "circe-generic" % circeVersion)
  .settings(coverageExcludedPackages := "io\\.finch\\..*;")
  .settings(
    (run / javaOptions) ++= Seq(
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
  "scalafix --check",
  "scalafmtCheckAll",
  "compile",
  "test:compile",
  "coverage",
  "test",
  "coverageReport",
  "coverageAggregate"
)
addCommandAlias("validate", validateCommands.mkString(";", ";", ""))
addCommandAlias("fmt", "all compile:scalafix; all test:scalafix; scalafmtAll")
