package me.milan.concurrent

import cats.Parallel
import cats.effect.{Async, ContextShift, IO, Timer}
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.parallel._
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

class MultiThreadingC[F[_]: Async: ContextShift: Timer: Parallel](executionContext: ExecutionContext) {

  implicit val ec: ExecutionContext = executionContext

  val underlying: Logger = LoggerFactory.getLogger(classOf[MultiThreadingC[F]])
  val unsafeLogger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLoggerFromSlf4j[F](underlying)

  def runMulti(toRunF: F[Unit], toRunIO: IO[Unit]): F[Unit] = (1 to 10)
    .toList
    .parTraverse { _ =>
      (
        toRunF.flatTap(_ => unsafeLogger.info("runF")),
        Async.liftIO(toRunIO).flatTap(_ => unsafeLogger.info("runIO")),
        Async.fromFuture(Async[F].delay(toRunIO.unsafeToFuture())).flatTap(_ => unsafeLogger.info("runFuture")),
        Async[F].async[Unit](cb => cb(Right(()))).flatTap(_ => unsafeLogger.info("runCallback")),
        Timer[F].sleep(Random.between(1000, 3000).millis).flatTap(_ => unsafeLogger.info("sleeping")),
        keepBusy.flatTap(_ => unsafeLogger.info("keeping busy")),
      )
        .parMapN {
          case (_, _, _, _, _, _) => ()
        }
    }.void

  private def keepBusy: F[Unit] = Async[F].delay((1 to 10000).foreach(i => s"$i"))

}
