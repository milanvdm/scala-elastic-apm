package me.milan.main.akka

import akka.NotUsed
import akka.stream.{ FlowShape, Graph }
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Keep, ZipWith }

object PassThroughFlow {
  def apply[A, T](processingFlow: Flow[A, T, NotUsed]): Graph[FlowShape[A, (T, A)], NotUsed] =
    apply[A, T, (T, A)](processingFlow, Keep.both)

  def apply[A, T, O](
    processingFlow: Flow[A, T, NotUsed],
    output: (T, A) => O
  ): Graph[FlowShape[A, O], NotUsed] =
    apply[A, A, T, O](processingFlow, identity, output)

  def apply[A, B, T, O](
    processingFlow: Flow[B, T, NotUsed],
    extractor: A => B,
    output: (T, A) => O
  ): Graph[FlowShape[A, O], NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[A](2))
      val zip = builder.add(ZipWith[T, A, O]((left, right) => output(left, right)))

      // format: off
      broadcast.out(0) ~> Flow.fromFunction(extractor) ~> processingFlow ~> zip.in0
      broadcast.out(1)         ~>           zip.in1
      // format: on

      FlowShape(broadcast.in, zip.out)
    })
}
