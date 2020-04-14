package me.milan

import cats.effect.{Concurrent, ContextShift, Resource}
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client.{SttpBackend, SttpBackendOptions}

object HttpRequest {

  def init[F[_]: Concurrent: ContextShift]: Resource[F, SttpBackend[F, Nothing, WebSocketHandler]] = AsyncHttpClientCatsBackend.resource[F](
    SttpBackendOptions.Default.httpProxy("localhost", 8079)
  )

}
