name := "tileserver"

resolvers ++= Seq(
  "ecc" at "https://github.com/ElectronicChartCentre/ecc-mvn-repo/raw/master/releases",
  "velvia maven" at "http://dl.bintray.com/velvia/maven"
)

libraryDependencies ++= Seq(
  "ch.qos.logback"           % "logback-classic"          % "1.1.3",
  "com.rojoma"              %% "rojoma-json-v3"           % "3.3.0",
  "com.rojoma"              %% "rojoma-json-v3-jackson"   % "1.0.0" excludeAll(
    ExclusionRule(organization = "com.rojoma")),
  "com.rojoma"              %% "simple-arm-v2"            % "2.1.0",
  "com.socrata"             %% "socrata-http-client"      % "3.3.1" excludeAll(
    ExclusionRule(organization = "com.rojoma"),
    ExclusionRule(organization = "com.socrata", name = "socrata-thirdparty-utils_2.10")),
  "com.socrata"             %% "socrata-http-jetty"       % "3.3.1" excludeAll(
    ExclusionRule(organization = "com.rojoma"),
    ExclusionRule(organization = "com.socrata", name = "socrata-thirdparty-utils_2.10")),
  "com.socrata"             %% "socrata-thirdparty-utils" % "3.1.4",
  "com.socrata"             %% "soql-pack"                % "0.6.0",
  "com.typesafe"             % "config"                   % "1.3.0",
  "commons-codec"            % "commons-codec"            % "1.10",
  "commons-io"               % "commons-io"               % "2.4",
  "no.ecc.vectortile"        % "java-vector-tile"         % "1.0.7",
  "org.apache.curator"       % "curator-x-discovery"      % "2.8.0",
  "org.velvia"              %% "msgpack4s"                % "0.4.3"
)

// Test dependencies.
libraryDependencies ++= Seq(
  "org.mockito"              % "mockito-core"             % "1.10.19" % "test",
  "org.scalacheck"          %% "scalacheck"               % "1.12.4"  % "test"
)

val TestOptionNoTraces = "-oD"
val TestOptionShortTraces = "-oDS"
val TestOptionFullTraces = "-oDF"

testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, TestOptionNoTraces)

// Setup revolver.
Revolver.settings

// Require full coverage.
ScoverageSbtPlugin.ScoverageKeys.coverageMinimum := 100
