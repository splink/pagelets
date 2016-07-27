package org.splink.raven.page

import akka.stream.Materializer
import org.splink.raven.tree._
import play.api.mvc.{Request, AnyContent, Action}

import scala.concurrent.{ExecutionContext, Future}

object PageFactory {

  def print = ???

  def create(p: Pagelet, args: Arg*)(implicit ec: ExecutionContext, r: Request[_], m: Materializer): Future[Html] =
    p match {
      case t@Tree(id, children, combiner) =>
        Future.sequence(
          children.map { child =>
            create(child, args: _*)
          }).map(combiner)

      case l: Leaf[_] =>
        l.exec(args: _*)
    }

}