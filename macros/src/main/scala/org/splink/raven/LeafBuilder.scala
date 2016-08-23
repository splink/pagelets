package org.splink.raven

import akka.stream.Materializer
import org.splink.raven.Exceptions.NoFallbackException
import org.splink.raven.Mason._
import play.api.Logger
import play.api.mvc.{AnyContent, Request}

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Success, Failure, Try}


trait LeafBuilder {
  def build(leaf: Leaf[_, _], args: Seq[Arg], requestId: RequestId, isRoot: Boolean)(
    implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): Future[BrickResult]
}

class LeafBuilderImpl extends LeafBuilder {
  private val logger = Logger(getClass).logger
  import LeafTools._

  def build(leaf: Leaf[_, _], args: Seq[Arg], requestId: RequestId, isRoot: Boolean)(
    implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer) = {

    def execute(id: Symbol, isFallback: Boolean,
                f: Seq[Arg] => Future[BrickResult],
                fallback: (Seq[Arg], Throwable) => Future[BrickResult]) = {

      def messageFor(t: Throwable) = if (Option(t.getMessage).isDefined) t.getMessage else ""

      val startTime = System.currentTimeMillis()
      val s = if (isFallback) " fallback" else ""
      logger.info(s"$requestId Invoke$s pagelet $id")

      Try {
        f(args).map { result =>
          logger.info(s"$requestId Finish$s pagelet $id took ${System.currentTimeMillis() - startTime}ms")
          result
        }.recoverWith {
          case t: Throwable =>
            logger.warn(s"$requestId Exception in async$s pagelet $id '${messageFor(t)}'")
            fallback(args, t)
        }
      } match {
        case Failure(t) =>
          logger.warn(s"$requestId Exception in main$s pagelet $id '${messageFor(t)}'")
          fallback(args, t)
        case Success(result) => result
      }
    }


    val build = leaf.execute(leaf.info, _: Seq[Arg])

    val buildFallback = (a: Seq[Arg]) => leaf.fallback.map(f => leaf.execute(f, a)).getOrElse {
      Future.failed(NoFallbackException(leaf.id))
    }

    execute(leaf.id, isFallback = false, build,
      fallback = (args, t) =>
        execute(leaf.id, isFallback = true, buildFallback,
          fallback = (args, t) =>
            if (isRoot) Future.failed(t) else Future.successful(BrickResult.empty)
        ))
  }
}
