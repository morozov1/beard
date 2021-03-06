logLevel := Level.Warn

resolvers += "simplytyped" at "http://simplytyped.github.io/repo/releases"

addSbtPlugin("com.simplytyped" % "sbt-antlr4" % "0.8.2")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.9.2")

addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.4")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.2")
