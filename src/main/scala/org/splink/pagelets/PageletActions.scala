package org.splink.pagelets

import akka.stream.Materializer
import play.api.{Logger, Environment}
import play.api.http.Writeable
import play.api.mvc._
import play.twirl.api.Html

import scala.concurrent.{Future, ExecutionContext}

case class Head(title: String,
                metaTags: Seq[MetaTag] = Seq.empty,
                js: Option[Fingerprint] = None,
                css: Option[Fingerprint] = None)

case class PageStream(language: String,
                      head: Head,
                      body: HtmlStream,
                      js: Option[Fingerprint] = None)

case class Page(language: String,
                head: Head,
                body: String,
                js: Option[Fingerprint] = None)

case class ErrorPage(language: String,
                     title: String,
                     exception: Throwable)

trait PageletActions {

  def PageAction: PageActions
  def PageletAction: PageletActions

  trait PageActions {
    def async[T: Writeable](errorTemplate: ErrorPage => T)(
      title: String, tree: RequestHeader => Pagelet, args: Arg*)(template: (Request[_], Page) => T)(
                                  implicit ec: ExecutionContext, m: Materializer, env: Environment): Action[AnyContent]

    def stream(title: String, tree: RequestHeader => Pagelet, args: Arg*)(template: (Request[_], PageStream) => HtmlStream)(
                             implicit ec: ExecutionContext, m: Materializer, env: Environment): Action[AnyContent]
  }

  trait PageletActions {
    def async[T: Writeable](errorTemplate: ErrorPage => T)(
      plan: RequestHeader => Tree, id: Symbol)(template: (Request[_], Page) => T)(
                                     implicit ec: ExecutionContext, m: Materializer, env: Environment): Action[AnyContent]
  }

}

trait PageletActionsImpl extends PageletActions {
  self: Controller with PageBuilder with TreeTools with Resources =>
  private val log = Logger("PageletActions").logger

  override val PageAction = new PageActions {

    import HtmlStreamOps._

    override def async[T: Writeable](errorTemplate: ErrorPage => T)(
      title: String, tree: RequestHeader => Pagelet, args: Arg*)(template: (Request[_], Page) => T)(
                                      implicit ec: ExecutionContext, m: Materializer, env: Environment) = Action.async { implicit request =>
      val result = builder.build(tree(request), args: _*)
      mkPage(title, result).flatMap { page =>
        Future.sequence(result.cookies).map { cookies =>
          Ok(template(request, page)).withCookies(cookies.flatten.distinct: _*)
        }
      }.recover {
        case e: Throwable =>
          log.error(s"$e")
          InternalServerError(errorTemplate(ErrorPage(request2lang.language, title, e)))
      }
    }

    override def stream(title: String, tree: RequestHeader => Pagelet, args: Arg*)(template: (Request[_], PageStream) => HtmlStream)(
                                      implicit ec: ExecutionContext, m: Materializer, env: Environment) = Action { implicit request =>
      val result = builder.build(tree(request), args: _*)
      val page = mkPageStream(title, result)
      //TODO cookies
      //result.cookies.map { cookies =>
      //}
      //
      Ok.chunked(template(request, page))//.withCookies(cookies: _*)
    }

    def mkPage(title: String, result: PageletResult)(implicit ec: ExecutionContext, r: RequestHeader, env: Environment, m: Materializer) = {
      val jsFinger = resources.update(result.js)
      val jsTopFinger = resources.update(result.jsTop)
      val cssFinger = resources.update(result.css)

      val eventualBody = result.body.runFold("") { (acc, next) =>
        acc + next.utf8String
      }

      eventualBody.map { body =>
        Page(request2lang.language,
          Head(title, result.metaTags, jsTopFinger, cssFinger),
          body,
          jsFinger)
      }
    }

    def mkPageStream(title: String, result: PageletResult)(implicit ec: ExecutionContext, r: RequestHeader, env: Environment, m: Materializer) = {
      val jsFinger = resources.update(result.js)
      val jsTopFinger = resources.update(result.jsTop)
      val cssFinger = resources.update(result.css)

      PageStream(request2lang.language,
        Head(title, result.metaTags, jsTopFinger, cssFinger),
        result.body.map(b => Html(b.utf8String)),
        jsFinger)
    }
  }

  override val PageletAction = new PageletActions {
    override def async[T: Writeable](errorTemplate: ErrorPage => T)(
      plan: RequestHeader => Tree, id: Symbol)(template: (Request[_], Page) => T)(
                                              implicit ec: ExecutionContext, m: Materializer, env: Environment) = Action.async { request =>
      plan(request).find(id).map { p =>
        val args = request.queryString.map { case (key, values) =>
          Arg(key, values.head)
        }.toSeq

        PageAction.async(errorTemplate)(id.name, _ => p, args: _*)(template).apply(request)
      }.getOrElse {
        Future.successful(NotFound(s"$id does not exist"))
      }
    }
  }


}
