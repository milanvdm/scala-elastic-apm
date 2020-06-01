package me.milan.main.future

import co.elastic.apm.api.ElasticApm

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success, Try }

trait MonadError[F[_]] {
  def unit[T](t: T): F[T]
  def map[T, T2](fa: F[T])(f: T => T2): F[T2]
  def flatMap[T, T2](fa: F[T])(f: T => F[T2]): F[T2]

  def error[T](t: Throwable): F[T]
  protected def handleWrappedError[T](rt: F[T])(h: PartialFunction[Throwable, F[T]]): F[T]
  def handleError[T](rt: => F[T])(h: PartialFunction[Throwable, F[T]]): F[T] =
    Try(rt) match {
      case Success(v)                     => handleWrappedError(v)(h)
      case Failure(e) if h.isDefinedAt(e) => h(e)
      case Failure(e)                     => error(e)
    }

  def eval[T](t: => T): F[T] = map(unit(()))(_ => t)
  def flatten[T](ffa: F[F[T]]): F[T] = {
    println(s"8: ${ElasticApm.currentTransaction().getId}")
    map(flatMap[F[T], T](ffa) { x =>
      println(s"10: ${ElasticApm.currentTransaction().getId}")
      identity {
        println(s"12: ${ElasticApm.currentTransaction().getId}")
        x
      }
    }) { x =>
      println(s"11: ${ElasticApm.currentTransaction().getId}")
      x
    }
  }

  def fromTry[T](t: Try[T]): F[T] = t match {
    case Success(v) => unit(v)
    case Failure(e) => error(e)
  }
}

trait MonadAsyncError[F[_]] extends MonadError[F] {
  def async[T](register: (Either[Throwable, T] => Unit) => Canceler): F[T]
}

case class Canceler(cancel: () => Unit)

object syntax {
  implicit final class MonadErrorOps[F[_], A](private val r: F[A]) extends AnyVal {
    def map[B](f: A => B)(implicit ME: MonadError[F]): F[B] = ME.map(r)(f)
    def flatMap[B](f: A => F[B])(implicit ME: MonadError[F]): F[B] = ME.flatMap(r)(f)
    def >>[B](r2: F[B])(implicit ME: MonadError[F]): F[B] = ME.flatMap(r)(_ => r2)
    def handleError[T](h: PartialFunction[Throwable, F[A]])(implicit ME: MonadError[F]): F[A] = ME.handleError(r)(h)
  }

  implicit final class MonadErrorValueOps[F[_], A](private val v: A) extends AnyVal {
    def unit(implicit ME: MonadError[F]): F[A] = ME.unit(v)
  }
}

class FutureMonad(implicit ec: ExecutionContext) extends MonadAsyncError[Future] {
  override def unit[T](t: T): Future[T] = Future.successful(t)
  override def map[T, T2](fa: Future[T])(f: (T) => T2): Future[T2] = fa.map { x =>
    println(s"13: ${ElasticApm.currentTransaction().getId}")
    f(x)
  }
  override def flatMap[T, T2](fa: Future[T])(f: (T) => Future[T2]): Future[T2] =
    fa.flatMap {
      println(s"9: ${ElasticApm.currentTransaction().getId}")
      f
    }

  override def error[T](t: Throwable): Future[T] = Future.failed(t)
  override protected def handleWrappedError[T](rt: Future[T])(h: PartialFunction[Throwable, Future[T]]): Future[T] =
    rt.recoverWith(h)

  override def eval[T](t: => T): Future[T] = Future(t)

  override def async[T](register: (Either[Throwable, T] => Unit) => Canceler): Future[T] = {
    val p = Promise[T]()
    register {
      case Left(t)  => p.failure(t)
      case Right(t) => p.success(t)
    }
    p.future
  }
}
