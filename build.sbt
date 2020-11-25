name := "scala-apm-playground"

version := "0.1"

scalaVersion := "2.13.4"

resolvers += Resolver.mavenLocal

scalafmtOnCompile := true

cinnamon in run := true
enablePlugins(Cinnamon)

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % "2.2.0",
  "org.typelevel" %% "cats-effect" % "2.2.0",
  "co.fs2" %% "fs2-core" % "2.4.6",

  "io.chrisdavenport" %% "log4cats-slf4j"   % "1.1.1",
  "ch.qos.logback" % "logback-classic" % "1.2.3",

  "com.softwaremill.sttp.client" %% "core" % "2.2.9",
  "com.softwaremill.sttp.client" %% "async-http-client-backend-cats" % "2.2.9",
  "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % "2.2.9",

  "co.elastic.apm" % "apm-agent-attach" % "1.19.0",
  "co.elastic.apm" % "apm-agent-api" % "1.19.0",
  "co.elastic.apm" % "apm-opentracing" % "1.19.0",

  "org.tpolecat" %% "doobie-core"      % "0.9.4",
  "org.tpolecat" %% "doobie-h2"        % "0.9.4",

  "org.scalikejdbc" %% "scalikejdbc"       % "3.5.0",
  "com.h2database"  %  "h2"                % "1.4.200",

  "com.typesafe.akka" %% "akka-stream-kafka" % "2.0.5",
  "com.typesafe.akka" %% "akka-stream" % "2.6.10",

  Cinnamon.library.cinnamonCHMetrics,
  Cinnamon.library.cinnamonAkkaStream,
  Cinnamon.library.cinnamonOpenTracing
)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

addCommandAlias("update", ";dependencyUpdates; reload plugins; dependencyUpdates; reload return")
