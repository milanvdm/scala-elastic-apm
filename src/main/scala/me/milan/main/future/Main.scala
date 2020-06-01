package me.milan.main.future

import java.util.concurrent.ForkJoinPool

import cats.instances.future._
import cats.syntax.flatMap._
import cats.syntax.functor._
import co.elastic.apm.api.ElasticApm
import me.milan.apm.ElasticApmAgent
import org.slf4j.{ Logger, LoggerFactory }
import sttp.client._

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }

object Main extends App {

  val _ = ElasticApmAgent.start()

  implicit val executionContext: ExecutionContext =
    ExecutionContext.fromExecutorService(
      new ForkJoinPool(Runtime.getRuntime.availableProcessors)
    )

  val logger: Logger = LoggerFactory.getLogger("Main")

  val program =
    for {
      transaction <- Future(ElasticApm.startTransaction().setName("test-trace"))
      scope <- Future(transaction.activate())
      _ <- Future(logger.info("Starting"))
      dummyBacked = new DummyHttpBackend()
      _ <- Future
        .traverse(1 to 10) { _ =>
          val f1 = dummyBacked
            .send(basicRequest.get(uri"https://postman-echo.com/get?foo1=bar1"))
            .void
            .flatTap(_ => Future(logger.info("runF1")))

          Future.sequence(List(f1)).void
        }
        .void
      _ <- Future(logger.info("Finished"))
      _ <- Future(scope.close())
      _ <- Future(transaction.end())
    } yield ()

  Await.result(program, 30.seconds)

}
