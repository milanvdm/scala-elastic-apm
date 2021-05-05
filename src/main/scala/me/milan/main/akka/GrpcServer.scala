package me.milan.main.akka

import akka.actor.{ ActorSystem, BootstrapSetup }
import akka.actor.setup.ActorSystemSetup
import akka.grpc.scaladsl.ServiceHandler
import akka.http.scaladsl.Http
import me.milan.main.akka.Main.executionContext
import me.milan.persistence.protos.PersistenceServiceHandler

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

object GrpcServer extends App {

  def start(): Future[Http.ServerBinding] = {
    implicit val actorSystem: ActorSystem = ActorSystem(
      "grpc-server",
      ActorSystemSetup(BootstrapSetup().withDefaultExecutionContext(global))
    )

    val persistenceService = new GrpcPersistenceService()
    val grpcPersistenceServiceHandler = PersistenceServiceHandler(persistenceService)
    Http()
      .newServerAt("0.0.0.0", 8080)
      .bind(grpcPersistenceServiceHandler)
  }

  start()

}
