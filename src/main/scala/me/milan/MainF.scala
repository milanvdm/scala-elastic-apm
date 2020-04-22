package me.milan

import cats.instances.future._
import cats.syntax.functor._
import co.elastic.apm.api.ElasticApm
import me.milan.apm.ElasticApmAgent
import me.milan.concurrent.{ExecutorConfig, ExecutorServices, MultiThreadingF}
import org.slf4j.{Logger, LoggerFactory}
import scalikejdbc._
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object MainF extends App {

  val _ = ElasticApmAgent.startF()

  implicit val executionContext: ExecutionContext =
    ExecutionContext.fromExecutorService(
      ExecutorServices.fromConfigF(ExecutorConfig.ForkJoinPool)
    )

  val logger: Logger = LoggerFactory.getLogger("Main")

  val program =
    for {
      transaction <- Future(ElasticApm.startTransaction().setName("test-trace"))
      _ <- Future(transaction.activate())
      _ <- Future(logger.info("Starting"))
      implicit0(database: AutoSession) <- Future(Database.initF)
      implicit0(backend: SttpBackend[Future, Nothing, WebSocketHandler]) <- Future(HttpRequest.initF())
      _ <- new MultiThreadingF(executionContext).runMulti(
        () => basicRequest.get(uri"https://postman-echo.com/get?foo1=bar1").send().void,
        () => Future(sql"select 42".execute().apply())
      )
      _ <- Future(logger.info("Halfway"))
      _ <- new MultiThreadingF(executionContext).runMulti(
        () => Future(sql"select 42".execute().apply()),
        () =>  basicRequest.get(uri"https://postman-echo.com/get?foo2=bar2").send().void
      )
      _ <- Future(logger.info("Finished"))
      _ <- Future(transaction.end())
    } yield ()

  Await.result(program, 30.seconds)

}
