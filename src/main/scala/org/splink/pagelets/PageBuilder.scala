package org.splink.pagelets

import akka.stream.Materializer
import play.api.mvc.{AnyContent, Request}

import scala.concurrent.ExecutionContext

trait PageBuilder {
  def builder: PageBuilderService

  trait PageBuilderService {
    def build(pagelet: Pagelet, args: Arg*)(
      implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): PageletResult
  }

}

trait PageBuilderImpl extends PageBuilder {
  self: LeafBuilder =>

  override val builder = new PageBuilderService {
    val log = play.api.Logger("PageBuilder")

    override def build(p: Pagelet, args: Arg*)(
      implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer) = {
      val requestId = RequestId.create

      def rec(p: Pagelet): PageletResult = p match {
          case Tree(_, children, combiner) =>
            combiner(children.map(rec))
          case l: Leaf[_, _] =>
            leafBuilderService.build(l, args, requestId, isRoot = p.id == l.id)
        }

      rec(p)
    }
  }

}