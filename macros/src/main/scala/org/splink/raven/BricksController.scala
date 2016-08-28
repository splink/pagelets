package org.splink.raven

import akka.stream.Materializer
import org.splink.raven.Exceptions.PageletException
import org.splink.raven.Resources.Fingerprint
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

case class ErrorPage(language: String,
                     title: String,
                     exception: PageletException)


trait BricksController {
  implicit def resultOps(result: Result): ResultOperations#ResultOps

  def PageAction[T: Writeable](template: Page => T, errorTemplate: ErrorPage => T)(
    title: String, plan: RequestHeader => Part, args: Arg*)(
                                implicit ec: ExecutionContext, m: Materializer, env: Environment): Action[AnyContent]

  def PagePartAction[T: Writeable](template: Page => T, errorTemplate: ErrorPage => T)(
    plan: RequestHeader => Tree, id: Symbol)(
                                    implicit ec: ExecutionContext, m: Materializer, env: Environment): Action[AnyContent]
}

trait BricksControllerImpl extends BricksController with Controller {
  self: Mason with TreeTools with ResultOperations =>
  val log = Logger(getClass).logger

  override implicit def resultOps(result: Result): ResultOps = resultOps(result)

  override def PageAction[T: Writeable](template: Page => T, errorTemplate: ErrorPage => T)(
    title: String, plan: RequestHeader => Part, args: Arg*)(
                   implicit ec: ExecutionContext, m: Materializer, env: Environment) = Action.async { implicit request =>
    mason.build(plan(request), args: _*).map { result =>
      Ok(template(mkPage(title, result))).withCookies(result.cookies: _*)
    }.recover {
      case e: PageletException =>
        log.error(s"error $e")
        InternalServerError(errorTemplate(ErrorPage(request2lang.language, title, e)))
    }
  }

  override def PagePartAction[T: Writeable](template: Page => T, errorTemplate: ErrorPage => T)(
    plan: RequestHeader => Tree, id: Symbol)(
               implicit ec: ExecutionContext, m: Materializer, env: Environment) = Action.async { request =>
    plan(request).find(id).map { part =>
      val args = request.queryString.map { case (key, values) =>
        Arg(key, values.head)
      }.toSeq

      PageAction(template, errorTemplate)(id, _ => part, args: _*).apply(request)
    }.getOrElse {
      Future.successful(BadRequest(s"'$id' does not exist"))
    }
  }

  def mkPage(title: String, result: BrickResult)(implicit r: RequestHeader, env: Environment) = {
    val jsFinger = Resources.update(result.js)
    val jsTopFinger = Resources.update(result.jsTop)
    val cssFinger = Resources.update(result.css)

    Page(request2lang.language,
      Head(title, result.metaTags, jsTopFinger, cssFinger),
      result.body,
      jsFinger)
  }
}
