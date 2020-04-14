package me.milan

import java.util.concurrent.ScheduledExecutorService

import cats.effect.{ExitCode, IO, IOApp, Resource, SyncIO}
import cats.syntax.functor._
import co.elastic.apm.api.ElasticApm
import doobie.implicits._
import fs2.Stream
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import me.milan.apm.ElasticApmAgent
import me.milan.concurrent.{ExecutorConfig, ExecutorServices, MultiThreading, ScheduledExecutorServices}
import org.slf4j.{Logger, LoggerFactory}
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler

import scala.concurrent.ExecutionContext

object Main extends IOApp.WithContext {

  val _ = ElasticApmAgent.start[IO].unsafeRunSync()

  override protected def executionContextResource: Resource[SyncIO, ExecutionContext] =
      ExecutorServices
        .fromConfig[SyncIO](ExecutorConfig.ForkJoinPool)
        .map(ExecutionContext.fromExecutorService)

  override protected def schedulerResource: Resource[SyncIO, ScheduledExecutorService] =
    ScheduledExecutorServices.create[SyncIO]

  override def run(args: List[String]): IO[ExitCode] = {

    val underlying: Logger = LoggerFactory.getLogger("Main")
    val unsafeLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLoggerFromSlf4j[IO](underlying)

    val program =
      for {
        transaction <- Stream.eval(IO.delay(ElasticApm.startTransaction().setName("test-trace")))
        _ <- Stream.eval(IO.delay(transaction.activate()))
        _ <- Stream.eval(unsafeLogger.info("Starting"))
        database <- Stream.resource(Database.init[IO])
        implicit0(backend: SttpBackend[IO, Nothing, WebSocketHandler]) <- Stream.resource(HttpRequest.init[IO])
        _ <- Stream.eval(
          new MultiThreading[IO](executionContext).runMulti(
            basicRequest.get(uri"https://postman-echo.com/get?foo1=bar1").send().void,
            sql"select 41".query[Int].unique.transact(database).void
          )
        )
        _ <- Stream.eval(
          new MultiThreading[IO](executionContext).runMulti(
            sql"select 42".query[Int].unique.transact(database).void,
            basicRequest.get(uri"https://postman-echo.com/get?foo2=bar2").send().void
          )
        )
        _ <- Stream.eval(unsafeLogger.info("Finished"))
        _ <- Stream.eval(IO.delay(transaction.end()))
      } yield ()

    program.compile.drain.as(ExitCode.Success)
  }

}
