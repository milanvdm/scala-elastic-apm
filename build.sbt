name := "scala-apm-playground"

version := "0.1"

scalaVersion := "2.13.5"

resolvers += Resolver.mavenLocal

scalafmtOnCompile := true

run / cinnamon := true
enablePlugins(Cinnamon)
enablePlugins(AkkaGrpcPlugin)

libraryDependencies ++= Seq(
  "org.typelevel"                %% "cats-core"                        % "2.5.0",
  "org.typelevel"                %% "cats-effect"                      % "2.4.1",
  "co.fs2"                       %% "fs2-core"                         % "2.5.4",
  "io.chrisdavenport"            %% "log4cats-slf4j"                   % "1.1.1",
  "ch.qos.logback"               % "logback-classic"                   % "1.2.3",
  "com.softwaremill.sttp.client" %% "core"                             % "2.2.9",
  "com.softwaremill.sttp.client" %% "async-http-client-backend-cats"   % "2.2.9",
  "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % "2.2.9",
  "co.elastic.apm"               % "apm-agent-attach"                  % "1.24.0",
  "co.elastic.apm"               % "apm-agent-api"                     % "1.24.0",
  "co.elastic.apm"               % "apm-opentracing"                   % "1.24.0",
  "org.tpolecat"                 %% "doobie-core"                      % "0.12.1",
  "org.tpolecat"                 %% "doobie-h2"                        % "0.12.1",
  "org.scalikejdbc"              %% "scalikejdbc"                      % "3.5.0",
  "com.h2database"               % "h2"                                % "1.4.200",
  "com.typesafe.akka"            %% "akka-stream-kafka"                % "2.1.0",
  "com.typesafe.akka"            %% "akka-stream"                      % "2.6.15",
  "com.typesafe.akka"            %% "akka-discovery"                   % "2.6.15",
  "com.typesafe.akka"            %% "akka-slf4j"                       % "2.6.15",
  "com.typesafe.akka"            %% "akka-http"                        % "10.2.5",
  "com.typesafe.akka"            %% "akka-http-core"                   % "10.2.5",
  "com.typesafe.akka"            %% "akka-parsing"                     % "10.2.5",
  "com.typesafe.akka"            %% "akka-http2-support"               % "10.2.5",
  "org.wvlet.airframe"           %% "airframe-ulid"                    % "21.6.0",
  Cinnamon.library.cinnamonAkkaGrpc,
  Cinnamon.library.cinnamonAkkaHttp,
  Cinnamon.library.cinnamonAkkaStream,
  Cinnamon.library.cinnamonCHMetrics,
  Cinnamon.library.cinnamonOpenTracing
)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

addCommandAlias("fmt", ";scalafmtSbt;scalafmtAll")
addCommandAlias("update", ";dependencyUpdates; reload plugins; dependencyUpdates; reload return")
