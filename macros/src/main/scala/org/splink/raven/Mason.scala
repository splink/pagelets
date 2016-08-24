package org.splink.raven

import akka.stream.Materializer
import play.api.mvc.{AnyContent, Request}

import scala.concurrent.{ExecutionContext, Future}

trait Mason {
  def visualize(p: Part): String

  def build(pagelet: Part, args: Arg*)(implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[BrickResult]
}

class MasonImpl(builder: LeafBuilder) extends Mason {
  import play.api.Logger

  private val logger = Logger(getClass).logger

  override def visualize(p: Part) = {
    def space(layer: Int) = (0 to layer).map(_ => "-").mkString

    def mkArgsString(fnc: FunctionInfo[_]) =
      if (fnc.types.isEmpty) ""
      else "(" + fnc.types.map { case (name, typ) =>
        val index = typ.lastIndexOf(".")
        name + ": " + (if (index > -1) typ.substring(index + 1) else typ)
      }.mkString(", ") + ")"

    def rec(p: Part, layer: Int = 0): String = p match {
      case t: Tree =>
        val a = space(layer) + t.id + "\n"
        a + t.children.map(c => rec(c, layer + 1)).mkString
      case Leaf(id, fnc, _) =>
        space(layer) + id + mkArgsString(fnc) + "\n"
    }

    rec(p)
  }

  override def build(pagelet: Part, args: Arg*)(
    implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer) = {
    val requestId = RequestId.mkRequestId

    def rec(p: Part): Future[BrickResult] =
      p match {
        case Tree(id, children, combiner) =>
          val start = System.currentTimeMillis()
          logger.info(s"$requestId Invoke pagelet ${p.id}")

          Future.sequence(children.map(rec)).map(combiner).map { result =>
            logger.info(s"$requestId Finish pagelet ${p.id} took ${System.currentTimeMillis() - start}ms")
            result
          }

        case l: Leaf[_, _] =>
          val isRoot = pagelet.id == l.id
          builder.build(l, args, requestId, isRoot)
      }

    rec(pagelet)
  }
}

object RequestId {
  private val rnd = new scala.util.Random()

  def mkRequestId = RequestId((0 to 6).map { _ =>
    (rnd.nextInt(90 - 65) + 65).toChar
  }.mkString)
}

case class RequestId(id: String) {
  override def toString = id
}