name := "tileserver"

organization := "com.socrata"

version := "0.0.1"

scalaVersion := "2.10.4"

resolvers ++= Seq(
  "socrata maven" at "https://repository-socrata-oss.forge.cloudbees.com/release",
  "ecc" at "https://github.com/ElectronicChartCentre/ecc-mvn-repo/raw/master/releases"
)

libraryDependencies ++= Seq(
  "com.rojoma"              %% "rojoma-json-v3"           % "3.2.0",
  "com.socrata"             %% "socrata-http-client"      % "3.0.0-SNAPSHOT",
  "com.socrata"             %% "socrata-http-jetty"       % "3.0.0-SNAPSHOT",
  "com.socrata"             %% "socrata-thirdparty-utils" % "2.5.6",
  "net.databinder.dispatch" %% "dispatch-core"            % "0.11.2",
  "no.ecc.vectortile"        % "java-vector-tile"         % "1.0.1",
  "org.apache.commons"       % "commons-io"               % "1.3.2",
  "org.slf4j"                % "slf4j-simple"             % "1.7.2",
  "com.rojoma"              %% "simple-arm-v2"            % "2.0.0"
)

// libraryDependencies ++= Seq(
//   "org.scalacheck"          %% "scalacheck"         % "1.10.0" % "test",
//   "org.scalatest"           %% "scalatest"          % "2.2.0"  % "test"
// )

testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD")

// WARNING: -optimize is not recommended with akka, should that come up.
// NOTE: Having to remove -Xfatal-warnings because it chokes due to inliner issues.
// This really bothers me.
scalacOptions ++= Seq("-optimize", "-deprecation", "-feature", "-language:postfixOps", "-Xlint")

Revolver.settings
