package me.milan.main.io.alpakka

import java.util.concurrent.ScheduledExecutorService

import akka.actor.setup.ActorSystemSetup
import akka.actor.{ ActorSystem, BootstrapSetup }
import akka.kafka.scaladsl.Transactional
import akka.kafka.{ ConsumerSettings, Subscriptions }
import akka.stream.scaladsl.{ RestartSource, Sink }
import akka.stream.{ Materializer, RestartSettings }
import cats.effect.{ ExitCode, IO, IOApp, Resource, SyncIO }
import cats.~>
import com.lightbend.cinnamon.akka.stream.CinnamonAttributes._
import doobie.implicits._
import fs2.Stream
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.opentracing.util.GlobalTracer
import me.milan.apm.ElasticApmAgent
import me.milan.concurrent.io.MultiThreading
import me.milan.concurrent.{ ExecutorConfig, ExecutorServices, ScheduledExecutorServices }
import me.milan.db.Database
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ ByteArrayDeserializer, StringDeserializer }
import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object Main extends IOApp.WithContext {

  val _ = ElasticApmAgent.startF[IO].unsafeRunSync()

  override protected def executionContextResource: Resource[SyncIO, ExecutionContext] =
    ExecutorServices
      .fromConfigResource[SyncIO](ExecutorConfig.ForkJoinPool)
      .map(ExecutionContext.fromExecutorService)

  override protected def schedulerResource: Resource[SyncIO, ScheduledExecutorService] =
    ScheduledExecutorServices.createF[SyncIO]

  override def run(args: List[String]): IO[ExitCode] = {

    val underlying: Logger = LoggerFactory.getLogger("Main")
    val unsafeLogger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLoggerFromSlf4j[IO](underlying)

    val SyncIOtoIOArrow = new (SyncIO ~> IO) { def apply[X](sx: SyncIO[X]): IO[X] = sx.toIO }

    val program: Stream[IO, Unit] =
      for {
        _ <- Stream.eval(unsafeLogger.info("Starting"))
        ec <- Stream.resource(executionContextResource.mapK[SyncIO, IO](SyncIOtoIOArrow))
        actorSystem <- Stream.eval(
          IO.delay(
            ActorSystem(
              "apm-test",
              ActorSystemSetup(BootstrapSetup().withDefaultExecutionContext(ec))
            )
          )
        )
        implicit0(materializer: Materializer) <- Stream.eval(IO.delay(Materializer.createMaterializer(actorSystem)))
        config <- Stream.eval(IO.delay(actorSystem.settings.config.getConfig("akka.kafka.consumer")))
        consumerSettings <- Stream.eval(
          IO.delay(
            ConsumerSettings(config, new StringDeserializer, new ByteArrayDeserializer)
              .withBootstrapServers("localhost:9092")
              .withGroupId("123")
              .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
          )
        )
        database <- Stream.resource(Database.initF[IO])
//        implicit0(backend: SttpBackend[IO, Nothing, WebSocketHandler]) <- Stream.resource(HttpRequest.initF[IO])
        _ <- Stream.eval(
          IO.fromFuture(
            IO.delay(
              RestartSource
                .onFailuresWithBackoff(
                  RestartSettings(
                    minBackoff = 3.seconds,
                    maxBackoff = 30.seconds,
                    randomFactor = 0.2
                  )
                ) { () =>
                  Transactional
                    .source(
                      consumerSettings,
                      Subscriptions.topics("payments")
                    )
                    .map { message =>
                      val span = GlobalTracer.get().activeSpan()
                      span.setTag("type", "payment")
                      message
                    }
                    .mapAsync(36) { _ =>
                      new MultiThreading[IO](executionContext)
                        .runMulti(
                          sql"select 42".query[Int].unique.transact(database).void,
                          sql"select 42".query[Int].unique.transact(database).void
                        )
                        .unsafeToFuture()
                    }
                }
                .instrumentedRunWith(Sink.ignore)(name = "payment-stream", reportByName = true, traceable = false)
            )
          )
        )
        _ <- Stream.eval(unsafeLogger.info("Finished"))
      } yield ()

    program.compile.drain.as(ExitCode.Success)
  }

}
