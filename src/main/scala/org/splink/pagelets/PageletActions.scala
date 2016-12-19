package org.splink.pagelets

import akka.stream.Materializer
import akka.stream.scaladsl.{Concat, Source}
import akka.util.ByteString
import play.api.{Environment, Logger}
import play.api.http.Writeable
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

case class Head(title: String,
                metaTags: Seq[MetaTag] = Seq.empty,
                js: Option[Fingerprint] = None,
                css: Option[Fingerprint] = None)

case class PageStream(language: String,
                      head: Head,
                      body: Source[ByteString, _],
                      js: Option[Fingerprint] = None)

case class Page(language: String,
                head: Head,
                body: String,
                js: Option[Fingerprint] = None)

trait PageletActions {

  def PageAction: PageActions
  def PageletAction: PageletActions

  trait PageActions {
    def async[T: Writeable](onError: => Call)(
      title: String, tree: RequestHeader => Pagelet, args: Arg*)(template: (Request[_], Page) => T)(
                             implicit ec: ExecutionContext, m: Materializer, env: Environment): Action[AnyContent]

    def stream[T: Writeable](title: String, tree: RequestHeader => Pagelet, args: Arg*)(
      template: (Request[_], PageStream) => Source[T, _])(
      implicit ec: ExecutionContext, m: Materializer, env: Environment): Action[AnyContent]
  }

  trait PageletActions {
    def async[T: Writeable](onError: => Call)(
      plan: RequestHeader => Tree, id: Symbol)(template: (Request[_], Page) => T)(
                             implicit ec: ExecutionContext, m: Materializer, env: Environment): Action[AnyContent]
  }

}

trait PageletActionsImpl extends PageletActions {
  self: Controller with PageBuilder with TreeTools with Resources =>
  private val log = Logger("PageletActions").logger

  override val PageAction = new PageActions {

    override def async[T: Writeable](onError: => Call)(
      title: String, tree: RequestHeader => Pagelet, args: Arg*)(template: (Request[_], Page) => T)(
                                      implicit ec: ExecutionContext, m: Materializer, env: Environment) = Action.async { implicit request =>
      val result = builder.build(tree(request), args: _*)

      (for {
        page <- mkPage(title, result)
        cookies <- Future.sequence(result.cookies)
        mandatoryPageletFailed <- Future.sequence(result.mandatoryFailedPagelets)
      } yield {
        if(mandatoryPageletFailed.forall(!_))
          Ok(template(request, page)).withCookies(cookies.flatten.distinct: _*)
        else
          Redirect(onError, TEMPORARY_REDIRECT)

      }).recover {
        case e: Throwable =>
          log.error(s"$e")
          Redirect(onError, TEMPORARY_REDIRECT)
      }
    }

    override def stream[T: Writeable](title: String, tree: RequestHeader => Pagelet, args: Arg*)(template: (Request[_], PageStream) => Source[T, _])(
      implicit ec: ExecutionContext, m: Materializer, env: Environment) = Action { implicit request =>

      val result = builder.build(tree(request), args: _*)
      val page = mkPageStream(title, result)

      Ok.chunked(template(request, page))
    }

    def mkPage(title: String, result: PageletResult)(implicit ec: ExecutionContext, r: RequestHeader, env: Environment, m: Materializer) = {
      val (jsFinger, jsTopFinger, cssFinger) = updateResources(result)

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

    def updateResources(result: PageletResult)(implicit env: Environment) = {
      val jsFinger = resources.update(result.js)
      val jsTopFinger = resources.update(result.jsTop)
      val cssFinger = resources.update(result.css)
      (jsFinger, jsTopFinger, cssFinger)
    }

    def bodySourceWithCookies(result: PageletResult)(implicit ec: ExecutionContext) = {
      def cookieJs(cookies: Seq[Cookie]) = {
        val calls = cookies.map { c =>
          s"""setCookie('${c.name}', '${c.value}', ${c.maxAge.getOrElse(0)}, '${c.path}', '${c.domain.getOrElse("")}');"""
        }.mkString("\n")

        if (calls.nonEmpty) ByteString(
          s"""|<script>
              |function setCookie(a,b,c,d,e){var f="";if(c>0){var g=new Date;g.setTime(g.getTime()+24*c*36e5),f="; expires="+g.toUTCString()}var h="";e.length>0&&(h="; domain="+e),document.cookie=a+"="+b+f+"; path="+d+h}
              |window.onload = function() {
              | $calls
              |}</script>""".stripMargin) else ByteString.empty
      }

      val cookies = Future.sequence(result.cookies).map(cookies => cookieJs(cookies.flatten))

      Source.combine(result.body, Source.fromFuture(cookies))(Concat.apply).filter(_.nonEmpty)
    }

    def mkPageStream(title: String, result: PageletResult)(implicit ec: ExecutionContext, r: RequestHeader, env: Environment, m: Materializer) = {
      val (jsFinger, jsTopFinger, cssFinger) = updateResources(result)

      PageStream(request2lang.language,
        Head(title, result.metaTags, jsTopFinger, cssFinger),
        bodySourceWithCookies(result),
        jsFinger)
    }
  }

  override val PageletAction = new PageletActions {
    override def async[T: Writeable](onError: => Call)(
      plan: RequestHeader => Tree, id: Symbol)(template: (Request[_], Page) => T)(
                                      implicit ec: ExecutionContext, m: Materializer, env: Environment) = Action.async { request =>
      plan(request).find(id).map { p =>
        val args = request.queryString.map { case (key, values) =>
          Arg(key, values.head)
        }.toSeq

        PageAction.async(onError)(id.name, _ => p, args: _*)(template).apply(request)
      }.getOrElse {
        Future.successful(NotFound(s"$id does not exist"))
      }
    }
  }


}
