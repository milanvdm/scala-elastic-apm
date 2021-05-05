package me.milan.apm

import cats.effect.Sync
import co.elastic.apm.attach.ElasticApmAttacher
import co.elastic.apm.opentracing.ElasticApmTracer

import scala.jdk.CollectionConverters._

object ElasticApmAgent {

  val openTracerAgent: ElasticApmTracer = new ElasticApmTracer()

  val configuration: Map[String, String] = Map(
    "service-name" -> "apm-playground",
    "enable_log_correlation" -> "true",
//    "profiling_inferred_spans_enabled" -> "false",
//    "profiling_inferred_spans_min_duration" -> "1s",
    "application_packages" -> "me.milan",
    "log_level" -> "INFO",
    "disable_instrumentations" -> "kafka"
  )

  def startF[F[_]: Sync]: F[Unit] = Sync[F].delay {
    ElasticApmAttacher.attach(configuration.asJava)
  }

  def start(): Unit =
    ElasticApmAttacher.attach(configuration.asJava)

}
