name := "tileserver"

organization := "com.socrata"

scalaVersion := "2.10.4"

resolvers ++= Seq(
  "socrata maven" at "https://repository-socrata-oss.forge.cloudbees.com/release",
  "ecc" at "https://github.com/ElectronicChartCentre/ecc-mvn-repo/raw/master/releases"
)

libraryDependencies ++= Seq(
  "com.rojoma"              %% "rojoma-json-v3"           % "3.2.0",
  "com.rojoma"              %% "simple-arm-v2"            % "2.0.0",
  "com.socrata"             %% "socrata-http-client"      % "3.0.0",
  "com.socrata"             %% "socrata-http-jetty"       % "3.0.0",
  "com.socrata"             %% "socrata-thirdparty-utils" % "2.5.6",
  "com.typesafe"             % "config"                   % "1.2.1",
  "commons-codec"            % "commons-codec"            % "1.10",
  "commons-io"               % "commons-io"               % "2.4",
  "no.ecc.vectortile"        % "java-vector-tile"         % "1.0.1",
  "org.apache.curator"       % "curator-x-discovery"      % "2.7.0",
  "org.slf4j"                % "slf4j-simple"             % "1.7.2"
)

libraryDependencies ++= Seq(
  "org.mockito"              % "mockito-core"             % "1.9.5"  % "test",
  "org.scalacheck"          %% "scalacheck"               % "1.11.6" % "test",
  "org.scalatest"           %% "scalatest"                % "2.2.1"  % "test"
)

testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oD")

com.socrata.cloudbeessbt.SocrataCloudbeesSbt.socrataSettings(assembly = true)

// WARNING: -optimize is not recommended with akka, should that come up.
// NOTE: Having to remove -Xfatal-warnings because it chokes due to inliner issues.
// This really bothers me.
scalacOptions ++= Seq("-optimize",
                      "-deprecation",
                      "-feature",
                      "-language:postfixOps",
                      "-Xlint",
                      "-Xfatal-warnings")

// Setup revolver.
Revolver.settings

// Make "assembly" depend on "scalastyle".  
lazy val styletask = taskKey[Unit]("a task that wraps 'scalastyle' with no input parameters")

styletask := { val _ = (scalastyle in Compile).toTask("").value }

(Keys.`package` in Compile) <<= (Keys.`package` in Compile) dependsOn styletask

AssemblyKeys.assembly <<= AssemblyKeys.assembly dependsOn styletask

// Generate com.socrata.tileserver.BuildInfo
buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name,
                                   version,
                                   scalaVersion,
                                   sbtVersion,
                                   BuildInfoKey.action("buildTime") { System.currentTimeMillis })

buildInfoPackage := organization.value + "." + name.value
