resolvers ++= Seq(
  Classpaths.typesafeReleases,
  Classpaths.sbtPluginReleases,
  "jgit-repo" at "http://download.eclipse.org/jgit/maven",
  Resolver.url("scoverage-bintray", url("https://dl.bintray.com/sksamuel/sbt-plugins/"))(Resolver.ivyStylePatterns),
  Resolver.sonatypeRepo("snapshots")
)

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.0")
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.3.3")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.4")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.1")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.4")
addSbtPlugin("org.scalastyle" % "scalastyle-sbt-plugin" % "0.8.0")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.2.20")
addSbtPlugin("com.fortysevendeg"  % "sbt-microsites" % "0.4.0")

