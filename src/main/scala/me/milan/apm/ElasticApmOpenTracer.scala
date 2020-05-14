package me.milan.apm

import co.elastic.apm.opentracing.ElasticApmTracer
import com.lightbend.cinnamon.opentracing.TracerFactory
import io.opentracing.Tracer

class ElasticApmOpenTracer() extends TracerFactory {

  def create(): Tracer = new ElasticApmTracer()

}
