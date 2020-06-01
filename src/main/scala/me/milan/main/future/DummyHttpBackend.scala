package me.milan.main.future

import java.io.{ ByteArrayInputStream, File, FileOutputStream, InputStream }
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

import co.elastic.apm.api.ElasticApm
import io.netty.handler.codec.http.HttpHeaders
import org.asynchttpclient.AsyncHandler.State
import org.asynchttpclient.handler.StreamedAsyncHandler
import org.asynchttpclient.{
  AsyncHandler,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig,
  HttpResponseBodyPart,
  HttpResponseStatus,
  RequestBuilder,
  Response => AsyncResponse
}
import org.reactivestreams.{ Publisher, Subscriber, Subscription }
import sttp.client
import sttp.client.{
  ByteArrayBody,
  ByteBufferBody,
  FileBody,
  IgnoreResponse,
  InputStreamBody,
  MappedResponseAs,
  NoBody,
  Request,
  RequestBody,
  Response,
  ResponseAs,
  ResponseAsByteArray,
  ResponseAsFile,
  ResponseAsFromMetadata,
  ResponseAsStream,
  ResponseMetadata,
  StringBody
}
import sttp.model.{ Header, StatusCode }

import scala.collection.JavaConverters._
import scala.collection.immutable.Seq
import scala.concurrent.{ ExecutionContext, Future }

class DummyHttpBackend(implicit executionContext: ExecutionContext) {

  val monad = new FutureMonad()

  val configBuilder: DefaultAsyncHttpClientConfig.Builder = new DefaultAsyncHttpClientConfig.Builder()
    .setCookieStore(null)

  val asyncClient = new DefaultAsyncHttpClient(configBuilder.build())

  def send[T](r: Request[T, Nothing]): Future[Response[T]] = {
    val rb = new RequestBuilder(r.method.method)
      .setUrl(r.uri.toString)
    rb.build()

    val request = asyncClient.prepareRequest(rb)

    val uuid = UUID.randomUUID().toString

    monad
      .flatten(
        monad
          .async[Future[Response[T]]] { cb =>
            def success(r: Future[Response[T]]): Unit = {
              println(s"2: $uuid - ${ElasticApm.currentTransaction().getId}")
              cb(Right(r))
            }

            def error(t: Throwable): Unit = {
              println(s"3: $uuid - ${ElasticApm.currentTransaction().getId}")
              cb(Left(t))
            }

            println(s"1: $uuid - ${ElasticApm.currentTransaction().getId}")

            val lf = request.execute(streamingAsyncHandler(r.response, success, error))
            Canceler(() => lf.abort(new InterruptedException))
          }
          .map { x =>
            println(s"4: $uuid - ${ElasticApm.currentTransaction().getId}")
            x
          }
      )
      .map { x =>
        println(s"5: $uuid - ${ElasticApm.currentTransaction().getId}")
        x
      }

  }

  def streamingAsyncHandler[T](
    responseAs: ResponseAs[T, Nothing],
    success: Future[Response[T]] => Unit,
    error: Throwable => Unit
  ): AsyncHandler[Unit] =
    new StreamedAsyncHandler[Unit] {
      private val builder = new AsyncResponse.ResponseBuilder()
      private var publisher: Option[Publisher[ByteBuffer]] = None
      private var completed = false

      override def onStream(p: Publisher[HttpResponseBodyPart]): AsyncHandler.State = {
        // Sadly we don't have .map on Publisher
        publisher = Some(new Publisher[ByteBuffer] {
          override def subscribe(s: Subscriber[_ >: ByteBuffer]): Unit =
            p.subscribe(new Subscriber[HttpResponseBodyPart] {
              override def onError(t: Throwable): Unit = s.onError(t)

              override def onComplete(): Unit = s.onComplete()

              override def onNext(t: HttpResponseBodyPart): Unit =
                s.onNext(t.getBodyByteBuffer)

              override def onSubscribe(v: Subscription): Unit =
                s.onSubscribe(v)
            })
        })
        // #2: sometimes onCompleted() isn't called, only onStream(); this
        // seems to be true esp for https sites. For these cases, completing
        // the request here.
        println(s"6: ${ElasticApm.currentTransaction().getId}")
        doComplete()
        State.CONTINUE
      }

      override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): AsyncHandler.State =
        throw new IllegalStateException("Requested a streaming backend, unexpected eager body parts.")

      override def onHeadersReceived(headers: HttpHeaders): AsyncHandler.State = {
        builder.accumulate(headers)
        State.CONTINUE
      }

      override def onStatusReceived(responseStatus: HttpResponseStatus): AsyncHandler.State = {
        builder.accumulate(responseStatus)
        State.CONTINUE
      }

      override def onCompleted(): Unit =
        // if the request had no body, onStream() will never be called
        doComplete()

      def saveFile(
        file: File,
        is: InputStream
      ): Unit = {
        if (!file.exists()) {
          if (file.getParentFile != null) {
            file.getParentFile.mkdirs()
          }
          file.createNewFile()
        }

        new FileOutputStream(file)
      }

      def publisherToFile(
        p: Publisher[ByteBuffer],
        f: File
      ): Future[Unit] =
        publisherToBytes(p).map(bytes => saveFile(f, new ByteArrayInputStream(bytes)))

      def publisherToStreamBody(p: Publisher[ByteBuffer]): Nothing =
        throw new IllegalStateException("This backend does not support streaming")

      def publisherToBytes(p: Publisher[ByteBuffer]): Future[Array[Byte]] =
        monad.async { cb =>
          def success(r: ByteBuffer): Unit = cb(Right(r.array()))

          def error(t: Throwable): Unit = cb(Left(t))

          val subscriber = new SimpleSubscriber(success, error)
          p.subscribe(subscriber)

          Canceler(() => subscriber.cancel())
        }

      private def doComplete(): Unit =
        if (!completed) {
          completed = true

          val baseResponse = readResponseNoBody(builder.build())
          val p = publisher.getOrElse(EmptyPublisher)
          val b = handleBody(p, responseAs, baseResponse)

          println(s"7: ${ElasticApm.currentTransaction().getId}")
          success(b.map(t => baseResponse.copy(body = t)))
        }

      private def handleBody[TT](
        p: Publisher[ByteBuffer],
        r: ResponseAs[TT, _],
        responseMetadata: ResponseMetadata
      ): Future[TT] =
        r match {
          case MappedResponseAs(raw, g) =>
            val nested = handleBody(p, raw, responseMetadata)
            nested.map(g(_, responseMetadata))
          case ResponseAsFromMetadata(f) => handleBody(p, f(responseMetadata), responseMetadata)
          case _: ResponseAsStream[_, _] => monad.unit(publisherToStreamBody(p).asInstanceOf[TT])
          case IgnoreResponse            =>
            // getting the body and discarding it
            publisherToBytes(p).map(_ => ())

          case ResponseAsByteArray =>
            publisherToBytes(p).map(b => b) // adjusting type because ResponseAs is covariant

          case ResponseAsFile(file) =>
            publisherToFile(p, file.toFile).map(_ => file)
        }

      override def onThrowable(t: Throwable): Unit =
        error(t)
    }

  def setBody(
    r: Request[_, Nothing],
    body: RequestBody[Nothing],
    rb: RequestBuilder
  ): Unit =
    body match {
      case NoBody => // skip

      case StringBody(b, encoding, _) =>
        rb.setBody(b.getBytes(encoding))

      case ByteArrayBody(b, _) =>
        rb.setBody(b)

      case ByteBufferBody(b, _) =>
        rb.setBody(b)

      case InputStreamBody(b, _) =>
        rb.setBody(b)

      case FileBody(b, _) =>
        rb.setBody(b.toFile)

    }

  private def readResponseNoBody(response: AsyncResponse): Response[Unit] =
    client.Response(
      (),
      StatusCode.unsafeApply(response.getStatusCode),
      response.getStatusText,
      readHeaders(response.getHeaders),
      Nil
    )

  private def readHeaders(h: HttpHeaders): Seq[Header] =
    h.iteratorAsString()
      .asScala
      .map(e => Header.notValidated(e.getKey, e.getValue))
      .toList

  def close(): Future[Unit] =
    monad.unit(())
}

object EmptyPublisher extends Publisher[ByteBuffer] {
  override def subscribe(s: Subscriber[_ >: ByteBuffer]): Unit =
    s.onComplete()
}

class SimpleSubscriber(
  success: ByteBuffer => Unit,
  error: Throwable => Unit
) extends Subscriber[ByteBuffer] {
  // a pair of values: (is cancelled, current subscription)
  private val subscription = new AtomicReference[(Boolean, Subscription)]((false, null))
  private val chunks = new ConcurrentLinkedQueue[Array[Byte]]()
  private var size = 0

  override def onSubscribe(s: Subscription): Unit = {
    assert(s != null)

    // The following can be safely run multiple times, as cancel() is idempotent
    val result = subscription.updateAndGet(new UnaryOperator[(Boolean, Subscription)] {
      override def apply(current: (Boolean, Subscription)): (Boolean, Subscription) = {
        // If someone has made a mistake and added this Subscriber multiple times, let's handle it gracefully
        if (current._2 != null) {
          current._2.cancel() // Cancel the additional subscription
        }

        if (current._1) { // already cancelled
          s.cancel()
          (true, null)
        } else { // happy path
          (false, s)
        }
      }
    })

    if (result._2 != null) {
      result._2.request(Long.MaxValue) // not cancelled, we can request data
    }
  }

  override def onNext(b: ByteBuffer): Unit = {
    assert(b != null)
    val a = b.array()
    size += a.length
    chunks.add(a)
  }

  override def onError(t: Throwable): Unit = {
    assert(t != null)
    chunks.clear()
    error(t)
  }

  override def onComplete(): Unit = {
    val result = ByteBuffer.allocate(size)
    chunks.asScala.foreach(result.put)
    chunks.clear()
    success(result)
  }

  def cancel(): Unit =
    // subscription.cancel is idempotent:
    // https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#specification
    // so the following can be safely retried
    subscription.updateAndGet(new UnaryOperator[(Boolean, Subscription)] {
      override def apply(current: (Boolean, Subscription)): (Boolean, Subscription) = {
        if (current._2 != null) current._2.cancel()
        (true, null)
      }
    })
}
