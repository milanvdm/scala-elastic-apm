package me.milan.http

import cats.effect.{ Concurrent, ContextShift, Resource }
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{ SttpBackend, SttpBackendOptions }

import scala.concurrent.{ ExecutionContext, Future }

object HttpRequest {

  def initF[F[_]: Concurrent: ContextShift]: Resource[F, SttpBackend[F, Nothing, WebSocketHandler]] =
    AsyncHttpClientCatsBackend.resource[F](
      SttpBackendOptions.Default.httpProxy("localhost", 3128)
    )

  def init()(implicit ec: ExecutionContext): SttpBackend[Future, Nothing, WebSocketHandler] =
    AsyncHttpClientFutureBackend(
      SttpBackendOptions.Default.httpProxy("localhost", 3128)
    )

}
