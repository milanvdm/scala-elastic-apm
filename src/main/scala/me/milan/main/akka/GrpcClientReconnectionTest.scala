package me.milan.main.akka

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import me.milan.persistence.protos.{ CreateIdRequest, PersistenceService, PersistenceServiceClient }
import org.slf4j.{ Logger, LoggerFactory }

object GrpcClientReconnectionTest extends App {

  val logger: Logger = LoggerFactory.getLogger("GrpcClientReconnectionTest")

  implicit val actorSystem: ActorSystem = ActorSystem()

  val persistenceServiceSettings = GrpcClientSettings.fromConfig(PersistenceService.name)
  val persistenceClient = PersistenceServiceClient(persistenceServiceSettings)

  val result = persistenceClient.createId(CreateIdRequest("test"))

  Thread.sleep(1000)

  println(result.isCompleted)
  println(result.value.get)

  persistenceClient.close()

}
