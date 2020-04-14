package me.milan

import cats.effect.{Async, Blocker, ContextShift, Resource}
import doobie.h2.H2Transactor
import me.milan.concurrent.{ExecutorConfig, ExecutorServices}

import scala.concurrent.ExecutionContext

object Database {

  def init[F[_]: Async: ContextShift]: Resource[F, H2Transactor[F]] = for {
    ec <- ExecutorServices.fromConfig[F](ExecutorConfig.ThreadPool(4)).map(ExecutionContext.fromExecutorService)
    be <- Blocker[F]
    xa <- H2Transactor.newH2Transactor[F](
      "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
      "sa",
      "",
      ec,
      be
    )
  } yield xa

}
