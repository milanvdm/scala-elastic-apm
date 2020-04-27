package me.milan.db

import cats.effect.{ Async, Blocker, ContextShift, Resource }
import doobie.h2.H2Transactor
import me.milan.concurrent.{ ExecutorConfig, ExecutorServices }
import scalikejdbc.{ AutoSession, ConnectionPool }

import scala.concurrent.ExecutionContext

object Database {

  def initF[F[_]: Async: ContextShift]: Resource[F, H2Transactor[F]] =
    for {
      ec <- ExecutorServices
        .fromConfigResource[F](ExecutorConfig.ThreadPool(4))
        .map(ExecutionContext.fromExecutorService)
      be <- Blocker[F]
      xa <- H2Transactor.newH2Transactor[F](
        "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        "sa",
        "",
        ec,
        be
      )
    } yield xa

  def init: AutoSession.type = {
    Class.forName("org.h2.Driver")
    ConnectionPool.singleton("jdbc:h2:mem:hello", "user", "pass")
    AutoSession
  }

}
