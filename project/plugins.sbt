externalResolvers ++= Seq(Resolver.url("socrata ivy releases", url("https://repo.socrata.com/artifactory/ivy-libs-release-local"))(Resolver.ivyStylePatterns))

addSbtPlugin("com.socrata" % "socrata-sbt-plugins" %"1.6.8")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")
