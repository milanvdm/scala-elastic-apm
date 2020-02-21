package me.milan.apm

import cats.effect.Sync
import co.elastic.apm.attach.ElasticApmAttacher

import scala.jdk.CollectionConverters._

object ElasticApmAgent {

  def start[F[_]: Sync]: F[Unit] = Sync[F].delay {
    val configuration: Map[String, String] = Map(
      "service-name" -> "apm-playground",
      "enable_log_correlation" -> "true",
      "application_packages" -> "me.milan",
      "log_level" -> "DEBUG"
    )

    ElasticApmAttacher.attach(configuration.asJava)
  }

}
