package org.splink.pagelets

import play.api.mvc.{AnyContent, Request}

trait PageBuilder {
  def builder: PageBuilderService

  trait PageBuilderService {
    def build(pagelet: Pagelet, args: Arg*)(implicit r: Request[AnyContent]): PageletResult
  }

}

trait PageBuilderImpl extends PageBuilder {
  self: LeafBuilder =>

  override val builder = new PageBuilderService {
    import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
    import akka.stream.{Attributes, FlowShape, Inlet, Outlet}

    val log = play.api.Logger("PageBuilder")

    override def build(pagelet: Pagelet, args: Arg*)(implicit r: Request[AnyContent]) = {
      val start = System.currentTimeMillis()
      val requestId = RequestId.create

      def rec(p: Pagelet): PageletResult = p match {
        case Tree(_, children, combiner) =>
          combiner(children.map(rec))
        case l: Leaf[_, _] =>
          leafBuilderService.build(l, args, requestId)
      }

      val result = rec(pagelet)
      result.copy(body = result.body.via(new Completion(start, requestId, pagelet)))
    }


    private class Completion[A](start: Long, requestId: RequestId, pagelet: Pagelet) extends GraphStage[FlowShape[A, A]] {
      val in = Inlet[A]("Completion.in")
      val out = Outlet[A]("Completion.out")

      val shape = FlowShape.of(in, out)

      override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
        new GraphStageLogic(shape) {
          setHandler(in, new InHandler {
            override def onPush(): Unit = push(out, grab(in))

            override def onUpstreamFinish(): Unit = {
              log.info(s"$requestId Finish page ${pagelet.id} took ${System.currentTimeMillis() - start}ms")
              complete(out)
            }

          })
          setHandler(out, new OutHandler {
            override def onPull(): Unit = pull(in)
          })
        }
    }

  }
}
