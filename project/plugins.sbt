externalResolvers ++= Seq("Socrata Artifactory" at "https://repo.socrata.com/artifactory/libs-release/",
  Resolver.url("Socrata", url("https://repo.socrata.com/artifactory/ivy-libs-release"))(Resolver.ivyStylePatterns))

addSbtPlugin("com.socrata" % "socrata-sbt-plugins" %"1.6.8")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")
