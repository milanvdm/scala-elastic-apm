package me.milan.opentracing

import cats.effect.{Resource, Sync}
import co.elastic.apm.opentracing.ElasticApmTracer
import io.opentracing.Tracer

object ElasticApmOpenTracing {

  def get[F[_]: Sync]: Resource[F, Tracer] = Resource.make(
    Sync[F].delay(new ElasticApmTracer)
  )(
    tracer => Sync[F].delay(tracer.close())
  )

}
