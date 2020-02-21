package me.milan.concurrent

sealed trait ExecutorConfig
object ExecutorConfig {
  case object CachedThreadPool extends ExecutorConfig
  case object ThreadPool extends ExecutorConfig
  case object ForkJoinPool extends ExecutorConfig
}
