package me.milan.concurrent

sealed trait ExecutorConfig
object ExecutorConfig {
  case object CachedThreadPool extends ExecutorConfig
  case class ThreadPool(poolsize: Int) extends ExecutorConfig
  case object ForkJoinPool extends ExecutorConfig
}
