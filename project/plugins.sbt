resolvers ++= Seq("sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/",
                  "Socrata Cloudbees" at "https://repository-socrata-oss.forge.cloudbees.com/release")


addSbtPlugin("com.socrata" % "socrata-sbt-plugins" %"1.5.0")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")
