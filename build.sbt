name := "tileserver"

scalaVersion := "2.11.7"
crossScalaVersions := Seq("2.10.4", scalaVersion.value)

scalacOptions ++= Seq("-optimize", "-Ywarn-dead-code")

scalacOptions <++= scalaVersion.map {
  case "2.10.4" => Seq.empty
  case _ => Seq("-Ywarn-unused-import")
}

scalacOptions in (Compile, console) := scalacOptions.value.filterNot(_.startsWith("-Ywarn-"))

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
  "com.socrata"             %% "socrata-curator-utils"    % "1.0.1" excludeAll(
    ExclusionRule(organization = "com.socrata", name = "socrata-http-client"),
    ExclusionRule(organization = "com.socrata", name = "socrata-http-jetty")),
  "com.socrata"             %% "socrata-http-common"      % "3.5.0" excludeAll(
    ExclusionRule(organization = "joda-time"),
    ExclusionRule(organization = "commons-codec"),
    ExclusionRule(organization = "commons-io"),
    ExclusionRule(organization = "com.rojoma")),
  "com.socrata"             %% "socrata-http-client"      % "3.5.0" excludeAll(
    ExclusionRule(organization = "commons-codec"),
    ExclusionRule(organization = "com.socrata", name = "socrata-http-common")),
  "com.socrata"             %% "socrata-http-jetty"       % "3.5.0" excludeAll(
    ExclusionRule(organization = "com.socrata", name = "socrata-http-common")),
  "com.socrata"             %% "soql-pack"                % "0.8.2" excludeAll(
    ExclusionRule(organization = "joda-time"),
    ExclusionRule(organization = "commons-io"),
    ExclusionRule(organization = "org.slf4j")),
  "com.socrata"             %% "socrata-test-common"      % "0.2.8-SNAPSHOT",
  "com.socrata"             %% "socrata-thirdparty-utils" % "4.0.1",
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
  "org.scalacheck"          %% "scalacheck"               % "1.12.4"  % "test"
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
