name := "helloworld"

organization := "com.socrata"

version := "0.0.1"

scalaVersion := "2.10.4"

resolvers ++= Seq(
  "socrata maven" at "https://repository-socrata-oss.forge.cloudbees.com/release",
  "Scala Tools Snapshots" at "http://scala-tools.org/repo-snapshots/",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "socrata maven-snap" at "https://repository-socrata-oss.forge.cloudbees.com/snapshot",
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
  Classpaths.sbtPluginReleases,
  "socrata internal maven" at "https://repo.socrata.com/artifactory/simple/libs-release-local",
  Resolver.url("socrata ivy", new URL("https://repo.socrata.com/artifactory/ivy-libs-release"))(Resolver.ivyStylePatterns)
)

libraryDependencies ++= Seq(
  "com.socrata" %% "socrata-http-jetty" % "2.0.0",
  "com.socrata" %% "socrata-http-client" % "2.0.0",
  "com.socrata" %% "socrata-http-curator-broker" % "2.0.0" exclude("org.jboss.netty", "netty"),
  "com.socrata" %% "socrata-thirdparty-utils" % "2.2.0",
  "com.socrata" %% "socrata-id"  % "2.1.1" withSources() withJavadoc(),
  "com.socrata" %% "socrata-zookeeper" % "0.1.3" withSources() withJavadoc(),
  "org.scalatest" % "scalatest_2.10" % "2.2.0" % "test" withSources() withJavadoc(),
  "org.scalacheck" %% "scalacheck" % "1.10.0" % "test" withSources() withJavadoc(),
  "com.rojoma" % "rojoma-json-v3_2.10" % "3.1.2" withSources() withJavadoc(),
  "au.com.bytecode" % "opencsv" % "2.4",
  "org.apache.commons" % "commons-lang3" % "3.3.2",
  "com.typesafe" % "config" % "1.0.2",
  "joda-time" % "joda-time" % "2.1",
  "org.mockito" % "mockito-core" % "1.9.5" withSources() withJavadoc(),
  "com.typesafe.slick"      %% "slick"                % "2.1.0",
  "postgresql"               % "postgresql"           % "9.1-901.jdbc4",
  "com.h2database"           % "h2"                   % "1.4.180",
  "com.mchange"              % "c3p0"                 % "0.9.5-pre8",
  "com.mchange"              % "mchange-commons-java" % "0.2.6.2"
)

testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD")

// WARNING: -optimize is not recommended with akka, should that come up.
// NOTE: Having to remove -Xfatal-warnings because it chokes due to inliner issues.
// This really bothers me.
scalacOptions ++= Seq("-optimize", "-deprecation", "-feature", "-language:postfixOps", "-Xlint")
