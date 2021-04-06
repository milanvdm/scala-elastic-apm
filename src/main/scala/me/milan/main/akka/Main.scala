package me.milan.main.akka

import akka.NotUsed
import akka.actor.setup.ActorSystemSetup
import akka.actor.{ ActorSystem, BootstrapSetup }
import akka.kafka.ConsumerMessage.PartitionOffset
import akka.kafka.ProducerMessage.{ Envelope, Results }
import akka.kafka.scaladsl.{ Producer, Transactional }
import akka.kafka.{ ConsumerSettings, ProducerMessage, ProducerSettings, Subscriptions }
import akka.stream.{ Materializer, RestartSettings }
import akka.stream.scaladsl.{ Flow, FlowWithContext, Keep, RestartSource, Sink }
import cats.instances.future._
import cats.syntax.functor._
import me.milan.apm.ElasticApmAgent
import me.milan.concurrent.future.MultiThreading
import me.milan.concurrent.{ ExecutorConfig, ExecutorServices }
import me.milan.db.Database
import me.milan.http.HttpRequest
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ ByteArrayDeserializer, StringDeserializer, StringSerializer }
import org.slf4j.{ Logger, LoggerFactory }
import scalikejdbc.{ AutoSession, _ }
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.{ SttpBackend, basicRequest, _ }
import com.lightbend.cinnamon.akka.stream.CinnamonAttributes.SourceWithInstrumented
import io.opentracing.util.GlobalTracer

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import org.apache.kafka.clients.producer.ProducerRecord

object Main extends App {

  // Call Grpc
  // Finish process

  ElasticApmAgent.start()

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

  val consumerConfig = actorSystem.settings.config.getConfig("akka.kafka.consumer")
  val consumerSettings =
    ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers("localhost:9092")
      .withGroupId("1234")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  val producerConfig = actorSystem.settings.config.getConfig("akka.kafka.producer")
  val producerSettings =
    ProducerSettings(producerConfig, new StringSerializer, new StringSerializer)
      .withBootstrapServers("localhost:9092")

  private val random = scala.util.Random

  val program =
    for {
      implicit0(database: AutoSession) <- Future(Database.init)
      implicit0(backend: SttpBackend[Future, Nothing, WebSocketHandler]) <- Future(HttpRequest.init())
      _ <- RestartSource
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
              println(message)
              val span = GlobalTracer.get().activeSpan()
              span.setTag("type", "payment")
              message
            }
            .map { message =>
              val paymentId = message.record.key()
              val span = GlobalTracer.get().activeSpan()
              span.setTag("payment-id", paymentId)
              message
            }
            .mapAsync(8) { message =>
              new MultiThreading(executionContext)
                .runMulti(
                  () => Future(sql"select 42".execute().apply()),
                  () => Future(sql"select 42".execute().apply())
                )
                .map(_ => message.partitionOffset)
            }
            .via(
              PassThroughFlow[
                PartitionOffset,
                Long,
                Long,
                PartitionOffset
              ](
                Flow[Long]
                  .map { offset =>
                    ProducerMessage
                      .single(new ProducerRecord("in-between", 0, random.nextInt.toString, "blah blah"), offset)
                      .withPassThrough(offset)
                  }
                  .via(
                    Flow[Envelope[String, String, Long]]
                      .map(envelope => envelope.withPassThrough(envelope.passThrough))
                      .via[Results[String, String, Long], NotUsed](
                        Producer
                          .flexiFlow[String, String, Long](producerSettings)
                      )
                      .map(_.passThrough)
                  ),
                (input: PartitionOffset) => input.offset,
                Keep.right
              )
            )
            .map { offset =>
              ProducerMessage
                .single(new ProducerRecord("test", 0, random.nextInt.toString, "blah blah"), offset)
            }
            .via(Transactional.flow(producerSettings, "transactionalId"))
        }
        .runWith(Sink.ignore)
    } yield ()

}
