package org.splink.raven

import akka.stream.Materializer
import org.splink.raven.Exceptions.PageletException
import play.api.mvc._
import play.api.{Environment, Logger}
import play.twirl.api.Html

import scala.concurrent.{ExecutionContext, Future}

trait BricksController extends Controller {

  case class Head(title: String,
                  meta: Option[Html] = None,
                  js: Option[Html] = None,
                  css: Option[Html] = None)

  case class Page(language: String,
                  head: Head,
                  body: Html,
                  js: Option[Html] = None)

  val logger = Logger(getClass).logger
  val builder = new LeafBuilderImpl

  def resourceFor(fingerprint: String) = ResourceAction(fingerprint)

  def Wall(template: Page => Html, resourceRoute: String => Call)(title: String, plan: Part, args: Arg*)(
                   implicit ec: ExecutionContext, m: Materializer, env: Environment) = Action.async { implicit request =>
    println(Mason.show(plan))
    Mason.build(builder)(plan, args: _*).map { result =>
      val fingerprints = Resource.update(result.js, result.css)

      val script = fingerprints.js.map { f =>
        Html(s"<script src='${resourceRoute(f.toString).url}'></script>")
      }

      val style = fingerprints.css.map { f =>
        Html(s"<link rel='stylesheet' media='screen' href='${resourceRoute(f.toString).url}'>")
      }

      Ok(
        template(Page(request2lang.language, Head(title, script, style), Html(result.body)))
      ).withCookies(result.cookies: _*)
    }.recover {
      case e: PageletException =>
        logger.error(s"error $e")
        InternalServerError(s"Error: $e")
    }
  }

  def WallPart(template: Page => Html, resourceRoute: String => Call)(plan: Tree, id: String)(
               implicit ec: ExecutionContext, m: Materializer, env: Environment) = Action.async { request =>
    import TreeTools._

    plan.find(Symbol(id)).map { part =>
      val args = request.queryString.map { case (key, values) =>
        Arg(key, values.head)
      }.toSeq

      Wall(template, resourceRoute)(id, part, args: _*).apply(request)
    }.getOrElse {
      Future.successful(BadRequest(s"Pagelet '$id' does not exist"))
    }
  }

}
