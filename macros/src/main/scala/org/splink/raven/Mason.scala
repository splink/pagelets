package org.splink.raven

import akka.stream.Materializer
import play.api.mvc.{AnyContent, Request}

import scala.concurrent.{ExecutionContext, Future}

trait Mason {
  def mason: MasonService

  trait MasonService {
    def build(pagelet: Part, args: Arg*)(implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[BrickResult]
  }
}

trait MasonImpl extends Mason {
  self: LeafBuilder =>

  override val mason = new MasonService {
    val log = play.api.Logger(getClass).logger

    override def build(pagelet: Part, args: Arg*)(
      implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer) = {
      val requestId = RequestId.mkRequestId

      def rec(p: Part): Future[BrickResult] =
        p match {
          case Tree(id, children, combiner) =>
            val start = System.currentTimeMillis()
            log.info(s"$requestId Invoke pagelet ${p.id}")

            Future.sequence(children.map(rec)).map(combiner).map { result =>
              log.info(s"$requestId Finish pagelet ${p.id} took ${System.currentTimeMillis() - start}ms")
              result
            }

          case l: Leaf[_, _] =>
            val isRoot = pagelet.id == l.id
            leafBuilderService.build(l, args, requestId, isRoot)
        }

      rec(pagelet)
    }
  }

}