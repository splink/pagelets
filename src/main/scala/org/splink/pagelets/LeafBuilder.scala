package org.splink.pagelets

import akka.stream.scaladsl.Source
import play.api.http.{CookiesConfiguration, HeaderNames}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait LeafBuilder {
  def leafBuilderService: LeafBuilderService

  trait LeafBuilderService {
    def build(leaf: Leaf[_, _], args: Seq[Arg], requestId: RequestId)(implicit r: Request[AnyContent]): PageletResult
  }

}

trait LeafBuilderImpl extends LeafBuilder {
  self: PageletActionBuilder with BaseController =>

  override val leafBuilderService = new LeafBuilderService with CookieHeaderEncoding {
    val log = play.api.Logger("LeafBuilder").logger
    implicit val ec: ExecutionContext = defaultExecutionContext
    val config: CookiesConfiguration = CookiesConfiguration()

    override def build(leaf: Leaf[_, _], args: Seq[Arg], requestId: RequestId)(implicit r: Request[AnyContent]) = {
      log.info(s"$requestId Invoke pagelet ${leaf.id}")

      def stacktraceFor(t: Throwable) = t.getStackTrace.map("    " + _).mkString("\n")

      def messageFor(t: Throwable) = if (Option(t.getMessage).isDefined) {
        t.getMessage + "\n" + stacktraceFor(t)
      } else "No message\n" + stacktraceFor(t)

      def mandatory = if(leaf.isMandatory) "mandatory" else ""

      val startTime = System.currentTimeMillis()

      actionService.execute(leaf.id, leaf.info, args).fold(t => {
        log.warn(s"$requestId TypeException in $mandatory pagelet ${leaf.id} '${messageFor(t)}'")
        PageletResult.empty.copy(mandatoryFailedPagelets = Seq(Future.successful(leaf.isMandatory)))
      }, action => {

        def lastFallback =
          if (leaf.isMandatory) Action(Results.InternalServerError) else Action(Results.Ok)

        def fallbackFnc =
          leaf.fallback.getOrElse(FunctionInfo(lastFallback _, Nil))

        def fallbackAction = actionService.execute(leaf.id, fallbackFnc, args).fold(t => {
          log.warn(s"$requestId TypeException in $mandatory pagelet fallback ${leaf.id} '${messageFor(t)}'")
          // fallback failed
          lastFallback
        }, action =>
          action
        )

        val eventualResult = Try {
          action(r).recoverWith { case t =>
            log.warn(s"$requestId Exception in async pagelet ${leaf.id} '${messageFor(t)}'")
            fallbackAction(r).recoverWith { case _ =>
              log.warn(s"$requestId Exception in $mandatory async pagelet fallback ${leaf.id} '${messageFor(t)}'")
              lastFallback(r)
            }
          }
        } match {
          case Failure(t) =>
            log.warn(s"$requestId Exception in pagelet ${leaf.id} '${messageFor(t)}'")
            Try(fallbackAction(r)) match {
              case Success(result) => result
              case Failure(_) =>
                log.warn(s"$requestId Exception in $mandatory pagelet fallback ${leaf.id} '${messageFor(t)}'")
                lastFallback(r)
            }
          case Success(result) => result
        }

        val bodySource = Source.future(eventualResult.map { result =>
          log.info(s"$requestId Finish pagelet ${leaf.id} took ${System.currentTimeMillis() - startTime}ms")
          result.body.dataStream
        }).flatMapConcat(identity)

        val cookies = eventualResult.map { result =>
          result.header.headers.get(HeaderNames.SET_COOKIE).
            map(decodeSetCookieHeader).getOrElse(Seq.empty)
        }

        val hasMandatoryPageletFailed = Seq(eventualResult.map(_.header.status == Results.InternalServerError.header.status))

        PageletResult(bodySource,
          leaf.javascript,
          leaf.javascriptTop,
          leaf.css, Seq(cookies),
          leaf.metaTags,
          hasMandatoryPageletFailed
        )
      })
    }
  }
}
