package me.milan.main.kafka

import java.util.Properties

import me.milan.apm.ElasticApmAgent
import me.milan.concurrent.future.MultiThreading
import me.milan.concurrent.{ ExecutorConfig, ExecutorServices }
import me.milan.db.Database
import me.milan.http.HttpRequest
import org.apache.kafka.clients.consumer.{ ConsumerConfig, ConsumerRecord, KafkaConsumer }
import org.slf4j.{ Logger, LoggerFactory }
import scalikejdbc.{ AutoSession, _ }
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.{ SttpBackend, basicRequest, _ }
import co.elastic.apm.api.ElasticApm
import cats.syntax.functor._
import cats.instances.future._

import scala.compat.java8.DurationConverters._
import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.jdk.CollectionConverters._

object Main extends App {

  val _ = ElasticApmAgent.start()

  implicit val executionContext: ExecutionContext =
    ExecutionContext.fromExecutorService(
      ExecutorServices.fromConfig(ExecutorConfig.ForkJoinPool)
    )

  val logger: Logger = LoggerFactory.getLogger("Main")

  val consumerProps: Properties = new Properties()
  consumerProps.put("group.id", "123")
  consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
  consumerProps.put(
    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
    "org.apache.kafka.common.serialization.StringDeserializer"
  )
  consumerProps.put(
    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
    "org.apache.kafka.common.serialization.StringDeserializer"
  )

  val consumer = new KafkaConsumer[String, String](
    consumerProps
  )

  val program =
    for {
      implicit0(database: AutoSession) <- Future(Database.init)
      implicit0(backend: SttpBackend[Future, Nothing, WebSocketHandler]) <- Future(HttpRequest.init())
      _ <- Future(consumer.subscribe(List("payments").asJava))
      message <- Future {
        var found = false
        var record: ConsumerRecord[String, String] = null

        while (!found) {
          val temp = consumer.poll(1.second.toJava).records("payments").asScala.headOption
          if (temp.nonEmpty) {
            record = temp.get
            found = true
          }
        }

        record

      }
      _ <- Future(ElasticApm.currentTransaction().addLabel("payment-id", message.value()))
      _ <- new MultiThreading(executionContext).runMulti(
        () => basicRequest.get(uri"https://postman-echo.com/get?foo2=bar2").send().void,
        () => Future(sql"select 42".execute().apply())
      )
      _ <- Future(logger.info("Finished"))
      _ <- Future(ElasticApm.currentTransaction().end())
    } yield ()

  Await.result(program, 30.seconds)

}
