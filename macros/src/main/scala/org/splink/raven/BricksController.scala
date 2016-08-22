package org.splink.raven

import akka.stream.Materializer
import org.splink.raven.Exceptions.PageletException
import play.api.mvc._
import play.api.{Environment, Logger}
import play.twirl.api.Html

import scala.concurrent.{ExecutionContext, Future}

trait BricksController {
  self: Controller =>

  case class Head(title: String,
                  meta: Option[Html] = None,
                  js: Option[Html] = None,
                  css: Option[Html] = None)

  case class Page(head: Head,
                  body: Html,
                  js: Option[Html] = None)

  val logger = Logger(getClass).logger

  def resourceFor(fingerprint: String) = ResourceAction(fingerprint)

  def RootPagelet(template: Page => Html, resourceRoute: String => Call)(
    title: String, pagelet: Pagelet, args: Arg*)(
                   implicit ec: ExecutionContext, m: Materializer, env: Environment) = Action.async { implicit request =>
    PageFactory.create(pagelet, args: _*).map { result =>
      val fingerprints = Resource.update(result.js, result.css)

      val script = fingerprints.js.map { f =>
        Html(s"<script src='${resourceRoute(f.toString).url}'></script>")
      }

      val style = fingerprints.css.map { f =>
        Html(s"<link rel='stylesheet' media='screen' href='${resourceRoute(f.toString).url}'>")
      }

      Ok(
        template(Page(Head(title, script, style), Html(result.body)))
      ).withCookies(result.cookies: _*)
    }.recover {
      case e: PageletException =>
        logger.error(s"error $e")
        InternalServerError(s"Error: $e")
    }
  }

  def Pagelet(template: Page => Html, resourceRoute: String => Call)(
    tree: Tree, id: String)(
               implicit ec: ExecutionContext, m: Materializer, env: Environment) = Action.async { request =>
    import TreeImplicits._
    tree.find(id).map { pagelet =>
      val args = request.queryString.map { case (key, values) =>
        Arg(key, values.head)
      }.toSeq

      RootPagelet(template, resourceRoute)(id, pagelet, args: _*).apply(request)
    }.getOrElse {
      Future.successful(BadRequest(s"Pagelet '$id' does not exist"))
    }
  }

}
