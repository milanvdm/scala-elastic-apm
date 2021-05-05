package me.milan.apm

import com.lightbend.cinnamon.opentracing.TracerFactory
import io.opentracing.Tracer

class ElasticApmOpenTracer() extends TracerFactory {

  def create(): Tracer = ElasticApmAgent.openTracerAgent

}
