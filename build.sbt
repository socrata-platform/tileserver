name := "helloworld"

organization := "com.socrata"

version := "0.0.1"

scalaVersion := "2.10.4"

resolvers ++= Seq(
  "socrata maven" at "https://repository-socrata-oss.forge.cloudbees.com/release"
)

libraryDependencies ++= Seq(
  "com.socrata"    %% "socrata-http-jetty" % "2.5.0-SNAPSHOT",
  "org.scalacheck" %% "scalacheck"         % "1.10.0" % "test" withSources() withJavadoc(),
  "org.scalatest"  %% "scalatest"          % "2.2.0"  % "test" withSources() withJavadoc()
)

testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD")

// WARNING: -optimize is not recommended with akka, should that come up.
// NOTE: Having to remove -Xfatal-warnings because it chokes due to inliner issues.
// This really bothers me.
scalacOptions ++= Seq("-optimize", "-deprecation", "-feature", "-language:postfixOps", "-Xlint")
