package me.milan.main.akka

import akka.actor.setup.ActorSystemSetup
import akka.actor.{ ActorSystem, BootstrapSetup }
import akka.kafka.scaladsl.Transactional
import akka.kafka.{ ConsumerSettings, Subscriptions }
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import cats.instances.future._
import cats.syntax.functor._
import co.elastic.apm.api.ElasticApm
import com.lightbend.cinnamon.akka.stream.CinnamonAttributes._
import me.milan.apm.ElasticApmAgent
import me.milan.concurrent.future.MultiThreading
import me.milan.concurrent.{ ExecutorConfig, ExecutorServices }
import me.milan.db.Database
import me.milan.http.HttpRequest
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ ByteArrayDeserializer, StringDeserializer }
import org.slf4j.{ Logger, LoggerFactory }
import scalikejdbc.{ AutoSession, _ }
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.{ SttpBackend, basicRequest, _ }

import scala.concurrent.{ ExecutionContext, Future }

object Main extends App {

  // Call Grpc
  // Finish process

  val _ = ElasticApmAgent.start()

  implicit val executionContext: ExecutionContext =
    ExecutionContext.fromExecutorService(
      ExecutorServices.fromConfig(ExecutorConfig.ForkJoinPool)
    )

  val logger: Logger = LoggerFactory.getLogger("Main")

  val actorSystem: ActorSystem = ActorSystem(
    "apm-test",
    ActorSystemSetup(BootstrapSetup().withDefaultExecutionContext(executionContext))
  )
  implicit val materializer: Materializer = Materializer.createMaterializer(actorSystem)

  val config = actorSystem.settings.config.getConfig("akka.kafka.consumer")
  val consumerSettings =
    ConsumerSettings(config, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers("localhost:9092")
      .withGroupId("123")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")

  val program =
    for {
      implicit0(database: AutoSession) <- Future(Database.init)
      implicit0(backend: SttpBackend[Future, Nothing, WebSocketHandler]) <- Future(HttpRequest.init())
      _ <- Transactional
        .source(
          consumerSettings,
          Subscriptions.topics("payments")
        )
        .asSourceWithContext(p => (p.record.key, p.partitionOffset))
        .asSource
        .map { message =>
          val paymentId = message._1.record.key()
          logger.info(ElasticApm.currentTransaction().getId())
          ElasticApm.currentTransaction().addLabel("payment-id", paymentId)
        }
        .mapAsync(4) { _ =>
          new MultiThreading(executionContext).runMulti(
            () => basicRequest.get(uri"https://postman-echo.com/get?foo1=bar1").send().void,
            () => Future(sql"select 42".execute().apply())
          )
        }
        .map(_ => ElasticApm.currentTransaction().end())
        .instrumentedRunWith(Sink.ignore)(name = "my-stream", traceable = true)
    } yield ()

}
