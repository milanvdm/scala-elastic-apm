name := "scala-apm-playground"

version := "0.1"

scalaVersion := "2.13.1"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % "2.1.0",
  "org.typelevel" %% "cats-effect" % "2.1.1",
  "co.fs2" %% "fs2-core" % "2.2.1",

  "io.chrisdavenport" %% "log4cats-slf4j"   % "1.0.1",
  "ch.qos.logback" % "logback-classic" % "1.2.3",

  "com.softwaremill.sttp.client" %% "core" % "2.0.0-RC13",
  "com.softwaremill.sttp.client" %% "async-http-client-backend-cats" % "2.0.0-RC13",

  "io.opentracing.contrib" % "opentracing-grpc" % "0.2.1",
  "io.opentracing.contrib" % "opentracing-scala-concurrent_2.13" % "0.0.6",
  "io.opentracing.contrib" % "opentracing-concurrent" % "0.4.0",
  "io.opentracing.contrib" % "opentracing-jdbc" % "0.2.8",

  "co.elastic.apm" % "apm-opentracing" % "1.15.0",

  "co.elastic.apm" % "apm-agent-attach" % "1.15.0",
  "co.elastic.apm" % "apm-agent-api" % "1.15.0",

  "org.tpolecat" %% "doobie-core"      % "0.8.8",
  "org.tpolecat" %% "doobie-h2"        % "0.8.8"
)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
