package me.milan

import cats.effect.{ExitCode, IO, IOApp, Resource, SyncIO}
import cats.syntax.functor._
import co.elastic.apm.api.ElasticApm
import doobie.implicits._
import fs2.Stream
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import me.milan.apm.ElasticApmAgent
import me.milan.concurrent.{ExecutorConfig, ExecutorServices, MultiThreading}
import org.slf4j.{Logger, LoggerFactory}
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Main extends IOApp.WithContext {

  val _ = ElasticApmAgent.start[IO].unsafeRunSync()

  override protected def executionContextResource: Resource[SyncIO, ExecutionContext] =
      ExecutorServices
        .fromConfig[SyncIO](ExecutorConfig.ForkJoinPool)
        .map(ExecutionContext.fromExecutorService)

  override def run(args: List[String]): IO[ExitCode] = {

    val underlying: Logger = LoggerFactory.getLogger("Main")
    val unsafeLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLoggerFromSlf4j[IO](underlying)

    val program =
      for {
        transaction <- Stream(ElasticApm.startTransaction().setName("test-trace"))
        scope <- Stream(transaction.activate())
        _ <- Stream.eval(unsafeLogger.info("Starting"))
        database <- Stream.resource(Database.init[IO](executionContext))
        implicit0(backend: SttpBackend[IO, Nothing, WebSocketHandler]) <- Stream.resource(HttpRequest.init[IO])
        _ <- Stream.eval(
          new MultiThreading[IO](executionContext).runMulti(
            () => sql"select 41".query[Int].unique.transact(database).void,
            () => basicRequest.get(uri"https://postman-echo.com/get?foo1=bar1").send[IO]().void.unsafeToFuture()
          )
        )
        _ <- Stream.eval(
          new MultiThreading[IO](executionContext).runMulti(
            () => sql"select 42".query[Int].unique.transact(database).void,
            () => basicRequest.get(uri"https://postman-echo.com/get?foo2=bar2").send[IO]().void.unsafeToFuture()
          )
        )
        _ <- Stream.sleep(5.seconds)
        _ <- Stream.eval(unsafeLogger.info("Finished"))
        _ <- Stream(scope.close())
        _ <- Stream(transaction.end())
      } yield ()

    program.compile.drain.as(ExitCode.Success)
  }

}
