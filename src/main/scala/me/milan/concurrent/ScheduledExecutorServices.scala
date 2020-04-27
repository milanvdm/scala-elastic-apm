package me.milan.concurrent

import java.util.concurrent.{ ScheduledExecutorService, Executors => JavaExecutors }

import cats.effect.{ Resource, Sync }
import cats.syntax.flatMap._

object ScheduledExecutorServices {

  def createF[F[_]: Sync]: Resource[F, ScheduledExecutorService] =
    Resource.make(
      Sync[F].delay(JavaExecutors.newScheduledThreadPool(2))
    )(es =>
      Sync[F]
        .delay(es.isShutdown)
        .ifM(
          Sync[F].unit,
          Sync[F].delay(es.shutdown())
        )
    )

}
