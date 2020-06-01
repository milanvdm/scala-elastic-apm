package me.milan.apm

import co.elastic.apm.attach.ElasticApmAttacher

import scala.jdk.CollectionConverters._

object ElasticApmAgent {

  val configuration: Map[String, String] = Map(
    "service-name" -> "apm-playground",
    "enable_log_correlation" -> "true",
//    "profiling_inferred_spans_enabled" -> "false",
//    "profiling_inferred_spans_min_duration" -> "1s",
    "application_packages" -> "me.milan",
    "log_level" -> "INFO",
    "disable_instrumentations" -> ""
  )

  def start(): Unit =
    ElasticApmAttacher.attach(configuration.asJava)

}
