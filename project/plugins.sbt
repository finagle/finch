resolvers ++= Seq(
  Classpaths.typesafeReleases,
  Classpaths.sbtPluginReleases
)

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.5.2")

addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "0.99.7.1")

addSbtPlugin("com.sksamuel.scoverage" %% "sbt-coveralls" % "0.0.5")
