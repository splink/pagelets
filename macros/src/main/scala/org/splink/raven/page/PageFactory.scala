package org.splink.raven.page

import akka.stream.Materializer
import org.splink.raven.tree._
import play.api.mvc.AnyContent

import scala.concurrent.{ExecutionContext, Future}

object PageFactory {

  def show(p: Pagelet)(implicit r: PageletRequest[AnyContent]) = {
    def space(layer: Int) = (0 to layer).map(_ => "-").mkString

    def mkArgsString(fnc: FunctionInfo[_]) =
      if (fnc.types.isEmpty) ""
      else "(" + fnc.types.map { case (name, typ) =>
        val index = typ.lastIndexOf(".")
        name + ": " + (if (index > -1) typ.substring(index + 1) else typ)
      }.mkString(", ") + ")"

    def rec(p: Pagelet, layer: Int = 0): String = p match {
      case t: Tree =>
        val a = space(layer) + t.id + "\n"
        a + t.children.map(c => rec(c, layer + 1)).mkString
      case Leaf(id, fnc) =>
        space(layer) + id + mkArgsString(fnc) + "\n"
    }

    rec(p)
  }

  def create(p: Pagelet, args: Arg*)(implicit ec: ExecutionContext, r: PageletRequest[AnyContent], m: Materializer): Future[Html] = {
    def rec(p: Pagelet): Future[Html] =
      p match {
        case t@Tree(id, children, combiner) =>
          Future.sequence(children.map(rec)).map(combiner)

        case l: Leaf[_] =>
          l.exec(args: _*)(ec, r.withPageletId(p.id), m)
      }

    rec(p)
  }
}