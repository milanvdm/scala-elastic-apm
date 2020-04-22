package me.milan.apm

import cats.effect.Sync
import co.elastic.apm.attach.ElasticApmAttacher

import scala.jdk.CollectionConverters._

object ElasticApmAgent {

  val configuration: Map[String, String] = Map(
    "service-name" -> "apm-playground",
    "enable_log_correlation" -> "true",
    "application_packages" -> "me.milan",
    "log_level" -> "INFO"
  )

  def startC[F[_]: Sync]: F[Unit] = Sync[F].delay {
    ElasticApmAttacher.attach(configuration.asJava)
  }

  def startF(): Unit =
    ElasticApmAttacher.attach(configuration.asJava)

}
