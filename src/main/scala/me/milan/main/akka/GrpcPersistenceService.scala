package me.milan.main.akka

import me.milan.persistence.protos._

import java.util.UUID
import scala.concurrent.Future

class GrpcPersistenceService() extends PersistenceService {

  override def createId(in: CreateIdRequest): Future[CreateIdResponse] =
    Future.successful(CreateIdResponse(UUID.randomUUID().toString))

}
