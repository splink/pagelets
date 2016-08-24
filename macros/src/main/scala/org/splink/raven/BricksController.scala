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

trait BricksController extends Controller {
  val logger = Logger(getClass).logger
  val mason = new MasonImpl(new LeafBuilderImpl)

  def resourceFor(fingerprint: String) = ResourceAction(fingerprint)

  def mkPage(title: String, result: BrickResult)(implicit r: RequestHeader, env: Environment) = {
    val fingerprints = Resource.update(result.js, result.css)

    Page(request2lang.language,
      Head(title, result.metaTags, fingerprints.js, fingerprints.css),
      result.body)
  }

  def Wall[T: Writeable](template: Page => T)(title: String, plan: Part, args: Arg*)(
                   implicit ec: ExecutionContext, m: Materializer, env: Environment) = Action.async { implicit request =>
    mason.build(plan, args: _*).map { result =>
      Ok(template(mkPage(title, result))).withCookies(result.cookies: _*)
    }.recover {
      case e: PageletException =>
        logger.error(s"error $e")
        InternalServerError(s"Error: $e")
    }
  }

  def WallPart[T: Writeable](template: Page => T)(plan: Tree, id: String)(
               implicit ec: ExecutionContext, m: Materializer, env: Environment) = Action.async { request =>
    import TreeTools._

    plan.find(Symbol(id)).map { part =>
      val args = request.queryString.map { case (key, values) =>
        Arg(key, values.head)
      }.toSeq

      Wall(template)(id, part, args: _*).apply(request)
    }.getOrElse {
      Future.successful(BadRequest(s"Pagelet '$id' does not exist"))
    }
  }

}
