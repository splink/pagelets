package org.splink.pagelets

import akka.stream.Materializer
import play.api.{Logger, Environment}
import play.api.http.Writeable
import play.api.mvc._

import scala.concurrent.{Future, ExecutionContext}

case class Head(title: String,
                metaTags: Seq[MetaTag] = Seq.empty,
                js: Option[Fingerprint] = None,
                css: Option[Fingerprint] = None)

case class Page(language: String,
                head: Head,
                body: String,
                js: Option[Fingerprint] = None)

case class ErrorPage(language: String,
                     title: String,
                     exception: Throwable)

trait PageletActions {
  def PageAction[T: Writeable](errorTemplate: ErrorPage => T)(
    title: String, tree: RequestHeader => Pagelet, args: Arg*)(template: (Request[_], Page) => T)(
                                implicit ec: ExecutionContext, m: Materializer, env: Environment): Action[AnyContent]

  def PageletAction[T: Writeable](errorTemplate: ErrorPage => T)(
    plan: RequestHeader => Tree, id: Symbol)(template: (Request[_], Page) => T)(
                                   implicit ec: ExecutionContext, m: Materializer, env: Environment): Action[AnyContent]
}

trait PageletActionsImpl extends PageletActions {
  self: Controller with PageBuilder with TreeTools with Resources =>
  private val log = Logger("PageletActions").logger

  override def PageAction[T: Writeable](errorTemplate: ErrorPage => T)(
    title: String, tree: RequestHeader => Pagelet, args: Arg*)(template: (Request[_], Page) => T)(
                                implicit ec: ExecutionContext, m: Materializer, env: Environment) = Action.async { implicit request =>
    builder.build(tree(request), args: _*).map { result =>
      Ok(template(request, mkPage(title, result))).withCookies(result.cookies: _*)
    }.recover {
      case e: Throwable =>
        log.error(s"$e")
        InternalServerError(errorTemplate(ErrorPage(request2lang.language, title, e)))
    }
  }

  override def PageletAction[T: Writeable](errorTemplate: ErrorPage => T)(
    plan: RequestHeader => Tree, id: Symbol)(template: (Request[_], Page) => T)(
                                   implicit ec: ExecutionContext, m: Materializer, env: Environment) = Action.async { request =>
    plan(request).find(id).map { p =>
      val args = request.queryString.map { case (key, values) =>
        Arg(key, values.head)
      }.toSeq

      PageAction(errorTemplate)(id.name, _ => p, args: _*)(template).apply(request)
    }.getOrElse {
      Future.successful(NotFound(s"$id does not exist"))
    }
  }

  def mkPage(title: String, result: PageletResult)(implicit r: RequestHeader, env: Environment) = {
    val jsFinger = resources.update(result.js)
    val jsTopFinger = resources.update(result.jsTop)
    val cssFinger = resources.update(result.css)

    Page(request2lang.language,
      Head(title, result.metaTags, jsTopFinger, cssFinger),
      result.body,
      jsFinger)
  }
}
