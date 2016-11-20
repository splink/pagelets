package org.splink.pagelets

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import play.api.mvc._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}


trait LeafBuilder {
  def leafBuilderService: LeafBuilderService

  trait LeafBuilderService {
    def build(leaf: Leaf[_, _], args: Seq[Arg], requestId: RequestId, isRoot: Boolean)(
      implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer): PageletResult
  }

}

trait LeafBuilderImpl extends LeafBuilder {
  self: LeafTools =>
  override val leafBuilderService = new LeafBuilderService {
    val log = play.api.Logger("LeafBuilder").logger

    override def build(leaf: Leaf[_, _], args: Seq[Arg], requestId: RequestId, isRoot: Boolean)(
      implicit ec: ExecutionContext, r: Request[AnyContent], m: Materializer) = {
      log.info(s"$requestId Invoke pagelet ${leaf.id}")

      def messageFor(t: Throwable) = if (Option(t.getMessage).isDefined) t.getMessage else "No message"

      val startTime = System.currentTimeMillis()

      leafService.execute(leaf.id, leaf.info, args).fold(t => {
        log.warn(s"$requestId TypeException in pagelet ${leaf.id} '${messageFor(t)}'")
        PageletResult.empty
      }, action => {
        def defaultFallback = Action(Results.Ok)
        def fallbackFnc = leaf.fallback.getOrElse(FunctionInfo(defaultFallback _, Nil))

        def fallbackAction = leafService.execute(leaf.id, fallbackFnc, args).fold(t => {
          log.warn(s"$requestId TypeException in pagelet fallback ${leaf.id} '${messageFor(t)}'")
          // fallback failed
          defaultFallback
        }, action =>
          action
        )

        val eventualResult = Try {
          action(r).recoverWith { case t =>
            log.warn(s"$requestId Exception in async pagelet ${leaf.id} '${messageFor(t)}'")
            fallbackAction(r).recoverWith { case _ =>
              log.warn(s"$requestId Exception in async pagelet fallback ${leaf.id} '${messageFor(t)}'")
              defaultFallback(r)
            }
          }
        } match {
          case Failure(t) =>
            log.warn(s"$requestId Exception in pagelet ${leaf.id} '${messageFor(t)}'")
            Try(fallbackAction(r)) match {
              case Success(result) => result
              case Failure(_) =>
                log.warn(s"$requestId Exception in pagelet fallback ${leaf.id} '${messageFor(t)}'")
                defaultFallback(r)
            }
          case Success(result) => result
        }

        val eventualBody = eventualResult.flatMap { result =>
          log.info(s"$requestId Finish pagelet ${leaf.id} took ${System.currentTimeMillis() - startTime}ms")
          result.body.consumeData
        }

        val bodySource = Source.fromFuture(eventualBody)

        PageletResult(bodySource, leaf.javascript, leaf.javascriptTop, leaf.css, Seq.empty, leaf.metaTags)
      }
      )
    }
  }
}
