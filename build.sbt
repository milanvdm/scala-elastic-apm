name := "scala-apm-playground"

version := "0.1"

scalaVersion := "2.13.2"

resolvers += Resolver.mavenLocal

scalafmtOnCompile := true

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % "2.1.0",

  "io.chrisdavenport" %% "log4cats-slf4j"   % "1.0.1",
  "ch.qos.logback" % "logback-classic" % "1.2.3",

  "com.softwaremill.sttp.client" %% "core" % "2.0.9",
  "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % "2.0.9",

  "co.elastic.apm" % "apm-agent-attach" % "1.16.1-SNAPSHOT",
  "co.elastic.apm" % "apm-agent-api" % "1.16.1-SNAPSHOT"
)

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
