name := "tileserver"
organization := "com.socrata"
scalaVersion := "2.10.4"

resolvers ++= Seq(
  "socrata maven" at "https://repository-socrata-oss.forge.cloudbees.com/release",
  "ecc" at "https://github.com/ElectronicChartCentre/ecc-mvn-repo/raw/master/releases"
)

libraryDependencies ++= Seq(
  "ch.qos.logback"           % "logback-classic"          % "1.1.2",
  "com.rojoma"              %% "simple-arm-v2"            % "2.1.0",
  "com.socrata"             %% "socrata-http-client"      % "3.2.0",
  "com.socrata"             %% "socrata-http-jetty"       % "3.2.0",
  "com.socrata"             %% "socrata-thirdparty-utils" % "3.0.0",
  "com.typesafe"             % "config"                   % "1.2.1",
  "commons-codec"            % "commons-codec"            % "1.10",
  "commons-io"               % "commons-io"               % "2.4",
  "no.ecc.vectortile"        % "java-vector-tile"         % "1.0.1",
  "org.apache.curator"       % "curator-x-discovery"      % "2.7.0"
)

libraryDependencies ++= Seq(
  "org.mockito"              % "mockito-core"             % "1.9.5"  % "test",
  "org.scalacheck"          %% "scalacheck"               % "1.11.6" % "test",
  "org.scalatest"           %% "scalatest"                % "2.2.1"  % "test"
)

val TestOptionNoTraces = "-oD"
val TestOptionShortTraces = "-oDS"
val TestOptionFullTraces = "-oDF"

testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, TestOptionNoTraces)

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

// Make "package" and "assembly" depend on "scalastyle".
lazy val styleTask = taskKey[Unit]("a task that wraps 'scalastyle' with no input parameters.")
styleTask := { val _ = (scalastyle in Compile).toTask("").value }
(Keys.`package` in Compile) <<= (Keys.`package` in Compile) dependsOn styleTask
AssemblyKeys.assembly <<= AssemblyKeys.assembly dependsOn styleTask

// Make "test:test" depend on "test:scalastyle"
lazy val testStyleTask = taskKey[Unit]("a task that wraps 'test:scalastyle' with no input parameters.")
testStyleTask := { val _ = (scalastyle in Test).toTask("").value }
(test in Test) <<= (test in Test) dependsOn (testStyleTask)

// Make test:scalastyle use scalastyle-test-config.xml
(scalastyleConfig in Test) := baseDirectory.value / "scalastyle-test-config.xml"

// Generate com.socrata.tileserver.BuildInfo
buildInfoSettings
sourceGenerators in Compile <+= buildInfo
buildInfoKeys := Seq[BuildInfoKey](name,
                                   version,
                                   scalaVersion,
                                   sbtVersion,
                                   BuildInfoKey.action("buildTime") { System.currentTimeMillis })

buildInfoPackage := organization.value + "." + name.value

// Disable scoverage highlighting required for scala version below 2.11.1
ScoverageSbtPlugin.ScoverageKeys.coverageHighlighting := false

// Warn on low coverage.
ScoverageSbtPlugin.ScoverageKeys.coverageMinimum := 100

// Fail on low coverage.
// This only fails cloudbees, as jenkins.sea1 does not run scoverage.
ScoverageSbtPlugin.ScoverageKeys.coverageFailOnMinimum := true
