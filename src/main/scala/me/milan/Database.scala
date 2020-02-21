package me.milan

import cats.effect.{Async, Blocker, ContextShift, Resource}
import doobie.h2.H2Transactor

import scala.concurrent.ExecutionContext

object Database {

  def init[F[_]: Async: ContextShift](executionContext: ExecutionContext): Resource[F, H2Transactor[F]] = for {
    be <- Blocker[F]
    xa <- H2Transactor.newH2Transactor[F](
      "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
      "sa",
      "",
      executionContext,
      be
    )
  } yield xa

}
