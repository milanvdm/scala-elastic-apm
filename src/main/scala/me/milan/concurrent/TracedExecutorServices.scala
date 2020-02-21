package me.milan.concurrent

import java.util.concurrent.ExecutorService

import cats.effect.{Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.opentracing.contrib.concurrent.TracedExecutorService
import me.milan.apm.ElasticApmOpenTracing

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

object TracedExecutorServices {

  def fromExecutorService[F[_]: Sync](
                                       executorService: ExecutorService
                                     ): Resource[F, ExecutionContextExecutorService] =
    ElasticApmOpenTracing
      .get
      .flatMap { tracer =>
        Resource.make(
          for {
            context <- Sync[F].delay(
              ExecutionContext.fromExecutorService(new TracedExecutorService(executorService, tracer))
            )
          } yield context
        )(
          context =>
            Sync[F]
              .delay(context.isShutdown)
              .ifM(
                Sync[F].unit,
                Sync[F].delay(context.shutdown())
              )
        )
      }

  def fromConfig[F[_]: Sync](
                              config: ExecutorConfig
                            ): Resource[F, ExecutionContextExecutorService] =
    ExecutorServices
      .fromConfig[F](config)
      .flatMap(fromExecutorService[F])
}
