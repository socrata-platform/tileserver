name := "tileserver"

organization := "com.socrata"

scalaVersion := "2.12.17"

externalResolvers := Seq(
  "Socrata SBT Repo" at "https://repo.socrata.com/artifactory/socrata-sbt-repo/",
  "Socrata Artifactory Libs Releases" at "https://repo.socrata.com/artifactory/libs-release/")

libraryDependencies ++= Seq(
  "ch.qos.logback"           % "logback-classic"          % "1.1.3",
  "com.rojoma"              %% "rojoma-json-v3"           % "3.15.0",
  "com.rojoma"              %% "rojoma-json-v3-jackson"   % "1.0.0",
  "com.rojoma"              %% "simple-arm-v2"            % "2.1.0",
  "com.socrata"             %% "socrata-curator-utils"    % "1.2.0",
  "com.socrata"             %% "socrata-http-common"      % "3.16.0",
  "com.socrata"             %% "socrata-http-client"      % "3.16.0",
  "com.socrata"             %% "socrata-http-jetty"       % "3.16.0",
  "com.socrata"             %% "soql-pack"                % "4.12.7",
  "com.socrata"             %% "socrata-thirdparty-utils" % "5.1.0",
  "com.typesafe"             % "config"                   % "1.2.1",
  "commons-codec"            % "commons-codec"            % "1.10",
  "commons-io"               % "commons-io"               % "2.4",
  "no.ecc.vectortile"        % "java-vector-tile"         % "1.0.8",
  "org.apache.curator"       % "curator-x-discovery"      % "2.8.0",
  "org.velvia"              %% "msgpack4s"                % "0.4.3"
)

// Test dependencies.
libraryDependencies ++= Seq(
  "com.socrata"             %% "socrata-test-common"      % "0.6.0"   % "test",
  "org.mockito"              % "mockito-core"             % "1.10.19" % "test",
  "org.scalacheck"          %% "scalacheck"               % "1.13.4"  % "test",
  "org.scalatest"           %% "scalatest"                % "3.0.0"   % "test",
)

evictionErrorLevel := Level.Warn

val TestOptionNoTraces = "-oD"
val TestOptionShortTraces = "-oDS"
val TestOptionFullTraces = "-oDF"

Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, TestOptionNoTraces)

assembly / assemblyMergeStrategy := {
  case PathList("module-info.class") =>
    MergeStrategy.discard
  case other =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(other)
}

enablePlugins(BuildInfoPlugin)

buildInfoOptions := Seq(
  BuildInfoOption.BuildTime,
)

assembly/assemblyJarName := s"${name.value}-assembly.jar"
