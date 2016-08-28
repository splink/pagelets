package org.splink.raven

import akka.stream.Materializer
import org.splink.raven.BrickResult.MetaTag
import org.splink.raven.Exceptions.PageletException
import org.splink.raven.Resource.Fingerprint
import play.api.http.Writeable
import play.api.mvc._
import play.api.{Environment, Logger}

import scala.concurrent.{ExecutionContext, Future}

case class Head(title: String,
                metaTags: Set[MetaTag] = Set.empty,
                js: Option[Fingerprint] = None,
                css: Option[Fingerprint] = None)

case class Page(language: String,
                head: Head,
                body: String,
                js: Option[Fingerprint] = None)

case class ErrorPage(language: String, title: String, exception: PageletException)

trait BricksController extends Controller {
  val logger = Logger(getClass).logger
  val mason = new MasonImpl(new LeafBuilderImpl)

  def resourceFor(fingerprint: String) = ResourceAction(fingerprint)

  def PageAction[T: Writeable](template: Page => T, errorTemplate: ErrorPage => T)(
    title: String, plan: RequestHeader => Part, args: Arg*)(
                   implicit ec: ExecutionContext, m: Materializer, env: Environment) = Action.async { implicit request =>
    mason.build(plan(request), args: _*).map { result =>
      Ok(template(mkPage(title, result))).withCookies(result.cookies: _*)
    }.recover {
      case e: PageletException =>
        logger.error(s"error $e")
        InternalServerError(errorTemplate(ErrorPage(request2lang.language, title, e)))
    }
  }

  def PagePartAction[T: Writeable](template: Page => T, errorTemplate: ErrorPage => T)(
    plan: RequestHeader => Tree, id: String)(
               implicit ec: ExecutionContext, m: Materializer, env: Environment) = Action.async { request =>
    import TreeTools._

    plan(request).find(Symbol(id)).map { part =>
      val args = request.queryString.map { case (key, values) =>
        Arg(key, values.head)
      }.toSeq

      PageAction(template, errorTemplate)(id, _ => part, args: _*).apply(request)
    }.getOrElse {
      Future.successful(BadRequest(s"'$id' does not exist"))
    }
  }

  def mkPage(title: String, result: BrickResult)(implicit r: RequestHeader, env: Environment) = {
    val jsFinger = Resource.update(result.js)
    val jsTopFinger = Resource.update(result.jsTop)
    val cssFinger = Resource.update(result.css)

    Page(request2lang.language,
      Head(title, result.metaTags, jsTopFinger, cssFinger),
      result.body,
      jsFinger)
  }
}
