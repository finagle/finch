resolvers ++= Seq(
  Classpaths.typesafeReleases,
  Classpaths.sbtPluginReleases,
  "jgit-repo" at "https://download.eclipse.org/jgit/maven",
  Resolver.sonatypeRepo("snapshots")
)

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.6.1")
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.3")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.2-1")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.13")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.12")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.0")
addSbtPlugin("com.47deg" % "sbt-microsites" % "1.3.4")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.4.10")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.21")
