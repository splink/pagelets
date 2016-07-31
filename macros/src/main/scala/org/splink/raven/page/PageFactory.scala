package org.splink.raven.page

import akka.stream.Materializer
import org.splink.raven.tree._
import play.api.Logger
import play.api.mvc.{AnyContent, Request}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Random, Try}

object PageFactory {

  def show(p: Pagelet)(implicit r: Request[AnyContent]) = {
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
      case Leaf(id, fnc, _) =>
        space(layer) + id + mkArgsString(fnc) + "\n"
    }

    rec(p)
  }

  def create(p: Pagelet, args: Arg*)(implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[Html] = {
    val requestId = uniqueString

    def rec(p: Pagelet): Future[Html] =
      p match {
        case Tree(id, children, combiner) =>
          val start = System.currentTimeMillis()
          Logger.info(s"$requestId Invoke pagelet ${p.id}")

          Future.sequence(children.map(rec)).map(combiner).map { result =>
            Logger.info(s"$requestId Finish pagelet ${p.id} took ${System.currentTimeMillis() - start}ms")
            result
          }

        case l: Leaf[_, _] => new LeafExecutor(l, requestId).run(args)
      }

    rec(p)
  }


  private class LeafExecutor(l: Leaf[_, _], requestId: String) {

    def run(args: Seq[Arg])(implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer) = {

      def execute(id: PageletId, isFallback: Boolean, f: Seq[Arg] => Future[Html], fallback: Seq[Arg] => Future[Html]) = {
        val startTime = System.currentTimeMillis()
        val s = if (isFallback) " fallback" else ""
        Logger.info(s"$requestId Invoke$s pagelet $id")

        Try {
          f(args).map { result =>
            Logger.info(s"$requestId Finish$s pagelet $id took ${System.currentTimeMillis() - startTime}ms")
            result
          }.recoverWith {
            case t: Throwable =>
              Logger.warn(s"$requestId Exception in async$s pagelet $id")
              fallback(args)
          }
        }.getOrElse {
          Logger.warn(s"$requestId Exception in main$s pagelet $id")
          fallback(args)
        }
      }

      execute(l.id, isFallback = false, l.run,
        fallback = _ =>
          execute(l.id, isFallback = true, l.runFallback,
            fallback = _ =>
              Future.successful(Html.empty)))
    }
  }

  val rnd = new Random()

  def uniqueString = (0 to 5).map { _ => (rnd.nextInt(90 - 65) + 65).toChar }.mkString
}