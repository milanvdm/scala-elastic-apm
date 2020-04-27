package me.milan.concurrent

import java.util.concurrent.{
  ExecutorService,
  ForkJoinPool,
  LinkedBlockingQueue,
  ThreadPoolExecutor,
  TimeUnit,
  Executors => JavaExecutors
}

import cats.effect.{ Resource, Sync }
import cats.syntax.flatMap._

object ExecutorServices {

  def fromConfigResource[F[_]: Sync](config: ExecutorConfig): Resource[F, ExecutorService] =
    Resource.make(
      Sync[F].delay(
        config match {
          case ExecutorConfig.CachedThreadPool =>
            JavaExecutors.newCachedThreadPool()
          case ExecutorConfig.ThreadPool(poolsize) =>
            new ThreadPoolExecutor(poolsize, 2 * poolsize, 60, TimeUnit.SECONDS, new LinkedBlockingQueue())
          case ExecutorConfig.ForkJoinPool =>
            new ForkJoinPool(Runtime.getRuntime.availableProcessors)
        }
      )
    )(es =>
      Sync[F]
        .delay(es.isShutdown)
        .ifM(
          Sync[F].unit,
          Sync[F].delay(es.shutdown())
        )
    )

  def fromConfig(config: ExecutorConfig): ExecutorService =
    config match {
      case ExecutorConfig.CachedThreadPool =>
        JavaExecutors.newCachedThreadPool()
      case ExecutorConfig.ThreadPool(poolsize) =>
        new ThreadPoolExecutor(poolsize, 2 * poolsize, 60, TimeUnit.SECONDS, new LinkedBlockingQueue())
      case ExecutorConfig.ForkJoinPool =>
        new ForkJoinPool(Runtime.getRuntime.availableProcessors)
    }

}
