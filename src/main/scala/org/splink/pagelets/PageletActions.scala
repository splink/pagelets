package org.splink.pagelets

import akka.stream.Materializer
import akka.stream.scaladsl.{Concat, Source}
import akka.util.ByteString
import play.api.http.Writeable
import play.api.mvc._
import play.api.{Environment, Logger}

import scala.concurrent.{ExecutionContext, Future}


case class Head(title: String,
                metaTags: Seq[MetaTag] = Seq.empty,
                js: Option[Fingerprint] = None,
                css: Option[Fingerprint] = None)

case class PageStream(head: Head,
                      body: Source[ByteString, _],
                      js: Option[Fingerprint] = None)

case class Page(head: Head,
                body: String,
                js: Option[Fingerprint] = None)

trait PageletActions {

  def PageAction: PageActions
  def PageletAction: PageletActions

  trait PageActions {
    def async[T: Writeable](onError: => Call)(title: RequestHeader => String, tree: RequestHeader => Pagelet, args: Arg*)(template: (Request[_], Page) => T)(
                             implicit m: Materializer, env: Environment): Action[AnyContent]

    def stream[T: Writeable](title: RequestHeader => String, tree: RequestHeader => Pagelet, args: Arg*)(template: (Request[_], PageStream) => Source[T, _])(
      implicit m: Materializer, env: Environment): Action[AnyContent]
  }

  trait PageletActions {
    def async[T: Writeable](onError: => Call)(tree: RequestHeader => Tree, id: PageletId)(template: (Request[_], Page) => T)(
                             implicit m: Materializer, env: Environment): Action[AnyContent]
  }

}

trait PageletActionsImpl extends PageletActions {
  self: BaseController with PageBuilder with TreeTools with Resources =>

  override val PageAction = new PageActions  {
    val log = Logger("PageletActions")

    implicit val ec: ExecutionContext = defaultExecutionContext

    override def async[T: Writeable](onError: => Call)(title: RequestHeader => String, tree: RequestHeader => Pagelet, args: Arg*)(template: (Request[_], Page) => T)(
                                      implicit m: Materializer, env: Environment) = Action.async { implicit request =>
      val result = builder.build(tree(request), args: _*)

      (for {
        page <- mkPage(title(request), result)
        res <- Future.sequence(result.results)
        mandatoryPageletFailed <- Future.sequence(result.mandatoryFailedPagelets)
      } yield {
        if(mandatoryPageletFailed.forall(!_)) {
          val initial = (Option.empty[Flash], Option.empty[Session], Seq.empty[Cookie])
          val (maybeFlash, maybeSession, cookies) = res.foldLeft(initial) { case ((flash, session, cookies), next) =>
           (flash.orElse(next._1), session.orElse(next._2), (cookies ++ next._3).distinct)
          }

          val nakedResult = Ok(template(request, page))
          (for {
            r1 <- maybeFlash.map(nakedResult.flashing).orElse(Some(nakedResult))
            r2 <- maybeSession.map(r1.withSession).orElse(Some(r1))
            r3 <- Some(if(cookies.nonEmpty) r2.withCookies(cookies:_*) else r2)
          } yield r3)
            .getOrElse(nakedResult).bakeCookies()

        } else {
          Redirect(onError, TEMPORARY_REDIRECT)
        }

      }).recover {
        case e: Throwable =>
          log.error(s"$e")
          Redirect(onError, TEMPORARY_REDIRECT)
      }
    }

    override def stream[T: Writeable](title: RequestHeader => String, tree: RequestHeader => Pagelet, args: Arg*)(template: (Request[_], PageStream) => Source[T, _])(
      implicit m: Materializer, env: Environment) = Action { implicit request =>

      val result = builder.build(tree(request), args: _*)
      val page = mkPageStream(title(request), result)

      Ok.chunked(template(request, page))
    }

    def mkPage(title: String, result: PageletResult)(implicit r: RequestHeader, env: Environment, m: Materializer) = {
      val (jsFinger, jsTopFinger, cssFinger) = updateResources(result)

      val eventualBody = result.body.runFold("")(_ + _.utf8String)

      eventualBody.map { body =>
        Page(Head(title, result.metaTags, jsTopFinger, cssFinger), body, jsFinger)
      }
    }

    def updateResources(result: PageletResult)(implicit env: Environment) = {
      val jsFinger = resources.update(result.js)
      val jsTopFinger = resources.update(result.jsTop)
      val cssFinger = resources.update(result.css)
      (jsFinger, jsTopFinger, cssFinger)
    }

    def bodySourceWithCookies(result: PageletResult) = {
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

      val cookies = Future.sequence(result.results).map(cookies => cookieJs(cookies.flatMap(_._3)))

      Source.combine(result.body, Source.future(cookies))(Concat.apply).filter(_.nonEmpty)
    }

    def mkPageStream(title: String, result: PageletResult)(implicit r: RequestHeader, env: Environment, m: Materializer) = {
      val (jsFinger, jsTopFinger, cssFinger) = updateResources(result)

      PageStream(Head(title, result.metaTags, jsTopFinger, cssFinger),
        bodySourceWithCookies(result),
        jsFinger)
    }
  }

  override val PageletAction = new PageletActions {
    override def async[T: Writeable](onError: => Call)(
      tree: RequestHeader => Tree, id: PageletId)(template: (Request[_], Page) => T)(
                                      implicit m: Materializer, env: Environment) = Action.async { request =>
      tree(request).find(id).map { p =>
        val args = request.queryString.map { case (key, values) =>
          Arg(key, values.head)
        }.toSeq

        PageAction.async(onError)(_ => id.name, _ => p, args: _*)(template).apply(request)
      }.getOrElse {
        Future.successful(NotFound(s"$id does not exist"))
      }
    }
  }


}
