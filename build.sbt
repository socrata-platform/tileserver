name := "tileserver"

resolvers ++= Seq(
  "ecc" at "https://github.com/ElectronicChartCentre/ecc-mvn-repo/raw/master/releases",
  "velvia maven" at "http://dl.bintray.com/velvia/maven"
)

libraryDependencies ++= Seq(
  "ch.qos.logback"           % "logback-classic"          % "1.1.2",
  "com.rojoma"              %% "rojoma-json-v3"           % "3.3.0",
  "com.rojoma"              %% "rojoma-json-v3-jackson"   % "1.0.0" excludeAll(
    ExclusionRule(organization = "com.rojoma")),
  "com.rojoma"              %% "simple-arm-v2"            % "2.1.0",
  "com.socrata"             %% "socrata-http-client"      % "3.3.0" excludeAll(
    ExclusionRule(organization = "com.rojoma"),
    ExclusionRule(organization = "com.socrata", name = "socrata-thirdparty-utils_2.10")),
  "com.socrata"             %% "socrata-http-jetty"       % "3.3.0" excludeAll(
    ExclusionRule(organization = "com.rojoma"),
    ExclusionRule(organization = "com.socrata", name = "socrata-thirdparty-utils_2.10")),
  "com.socrata"             %% "socrata-thirdparty-utils" % "3.0.0",
  "com.typesafe"             % "config"                   % "1.2.1",
  "commons-codec"            % "commons-codec"            % "1.10",
  "commons-io"               % "commons-io"               % "2.4",
  "no.ecc.vectortile"        % "java-vector-tile"         % "1.0.1",
  "org.apache.curator"       % "curator-x-discovery"      % "2.7.0",
  "org.velvia"              %% "msgpack4s"                % "0.4.3"
)

libraryDependencies ++= Seq(
  "org.mockito"              % "mockito-core"             % "1.9.5"  % "test",
  "org.scalacheck"          %% "scalacheck"               % "1.11.6" % "test",
  "org.scalatest"           %% "scalatest"                % "2.2.4"  % "test"
)

val TestOptionNoTraces = "-oD"
val TestOptionShortTraces = "-oDS"
val TestOptionFullTraces = "-oDF"

testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, TestOptionNoTraces)

// Setup revolver.
Revolver.settings

// Warn on low coverage.
ScoverageSbtPlugin.ScoverageKeys.coverageMinimum := 100
