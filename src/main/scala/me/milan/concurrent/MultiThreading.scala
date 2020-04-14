package me.milan.concurrent

import cats.Parallel
import cats.effect.{Async, ContextShift, Timer}
import cats.instances.list._
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.syntax.parallel._
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Random, Success}

class MultiThreading[F[_]: Async: ContextShift: Timer: Parallel](executionContext: ExecutionContext) {

  implicit val ec: ExecutionContext = executionContext

  val underlying: Logger = LoggerFactory.getLogger(classOf[MultiThreading[F]])
  val unsafeLogger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromSlf4j[F](underlying)

  def runMulti(toRunIO: () => F[Unit], toRunFuture: () => Future[Unit]): F[Unit] = (1 to 1)
    .toList
    .parTraverse { _ =>
      (
        toRunIO().flatTap(_ => unsafeLogger.info("runIO")),
        Async[F].async[Unit] { cb =>
          toRunFuture().onComplete {
            case Success(_) => cb(Right(()))
            case Failure(error) => cb(Left(error))
          }
        }.flatTap(_ => unsafeLogger.info("runFuture")),
        Timer[F].sleep(Random.between(1000, 3000).millis).flatTap(_ => unsafeLogger.info("sleeping")),
        keepBusy.flatTap(_ => unsafeLogger.info("keeping busy"))
      )
        .parMapN {
          case (_, _, _, _) => ()
        }
    }.void

  private def keepBusy: F[Unit] = Async[F].delay((1 to 100000).foreach(i => s"$i"))

}
