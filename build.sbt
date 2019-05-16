name := "tileserver"

organization := "com.socrata"

scalaVersion := "2.11.7"
crossScalaVersions := Seq("2.10.4", scalaVersion.value)

resolvers ++= Seq(
  "socrata sbt repo" at "https://repo.socrata.com/artifactory/socrata-sbt-repo/",
  "socrata releases" at "https://repo.socrata.com/artifactory/libs-release/",
  "ecc" at "https://github.com/ElectronicChartCentre/ecc-mvn-repo/raw/master/releases"
)

libraryDependencies ++= Seq(
  "ch.qos.logback"           % "logback-classic"          % "1.1.3",
  "com.rojoma"              %% "rojoma-json-v3"           % "3.4.1",
  "com.rojoma"              %% "rojoma-json-v3-jackson"   % "1.0.0",
  "com.rojoma"              %% "simple-arm-v2"            % "2.1.0",
  "com.socrata"             %% "socrata-curator-utils"    % "1.1.2" excludeAll(
    ExclusionRule(organization = "com.socrata", name = "socrata-http-client"),
    ExclusionRule(organization = "com.socrata", name = "socrata-http-jetty")),
  "com.socrata"             %% "socrata-http-common"      % "3.11.4" excludeAll(
    ExclusionRule(organization = "joda-time"),
    ExclusionRule(organization = "commons-codec"),
    ExclusionRule(organization = "commons-io"),
    ExclusionRule(organization = "com.rojoma")),
  "com.socrata"             %% "socrata-http-client"      % "3.11.4" excludeAll(
    ExclusionRule(organization = "commons-codec"),
    ExclusionRule(organization = "com.socrata", name = "socrata-http-common")),
  "com.socrata"             %% "socrata-http-jetty"       % "3.11.4" excludeAll(
    ExclusionRule(organization = "com.socrata", name = "socrata-http-common")),
  "com.socrata"             %% "soql-pack"                % "2.11.6" excludeAll(
    ExclusionRule(organization = "joda-time"),
    ExclusionRule(organization = "commons-io"),
    ExclusionRule(organization = "org.slf4j")),
  "com.socrata"             %% "socrata-test-common"      % "0.5.3",
  "com.socrata"             %% "socrata-thirdparty-utils" % "4.0.16",
  "com.typesafe"             % "config"                   % "1.2.1",
  "commons-codec"            % "commons-codec"            % "1.10",
  "commons-io"               % "commons-io"               % "2.4",
  "no.ecc.vectortile"        % "java-vector-tile"         % "1.0.8",
  "org.apache.curator"       % "curator-x-discovery"      % "2.8.0",
  "org.velvia"              %% "msgpack4s"                % "0.4.3"
)

// Test dependencies.
libraryDependencies ++= Seq(
  "org.mockito"              % "mockito-core"             % "1.10.19" % "test",
  "org.scalacheck"          %% "scalacheck"               % "1.13.4"  % "test"
)

val TestOptionNoTraces = "-oD"
val TestOptionShortTraces = "-oDS"
val TestOptionFullTraces = "-oDF"

testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, TestOptionNoTraces)

enablePlugins(sbtbuildinfo.BuildInfoPlugin)
// Setup revolver.
Revolver.settings

// Require full coverage.
ScoverageSbtPlugin.ScoverageKeys.coverageMinimum := 100
