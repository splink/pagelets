package controllers

import javax.inject._

import akka.stream.Materializer
import org.splink.raven.Exceptions.PageletException
import org.splink.raven.FunctionMacros._
import org.splink.raven.TwirlConversions._

import org.splink.raven.BrickResult._
import org.splink.raven._
import play.api.Environment
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import play.twirl.api.Html

import scala.concurrent.Future

@Singleton
class HomeController @Inject()(implicit m: Materializer, e: Environment) extends Controller {

  import TreeTools._

  val plan = Tree('root, Seq(
    Tree('first, Seq(
      Leaf('brick1, pagelet1 _).withFallback(fallbackPagelet _),
      Leaf('brick2, pagelet2 _).withFallback(fallbackPagelet _)
    ), results => combine(results)(views.html.test.apply)
    ))).skip('brick2).
      replace('brick1, Leaf('brick2, pagelet2 _)).
      replace('root, Leaf('brick1, newRoot _))

  val mason = new MasonImpl(new LeafBuilderImpl)

  def resourceFor(fingerprint: String) = ResourceAction(fingerprint)

  def pagelet(id: String) = Action.async { implicit request =>
    plan.find(Symbol(id)).map { part =>
      val args = request.queryString.map { case (key, values) =>
        Arg(key, values.head)
      }.toSeq

      mason.build(part, args: _*).map { result =>
        val fingerprints = Resource.update(result.js, result.css)
        val js = routes.HomeController.resourceFor(fingerprints.js.toString)
        val css = routes.HomeController.resourceFor(fingerprints.css.toString)

        Ok(
          views.html.pageletWrapper(id, js, css)(Html(result.body))
        ).withCookies(result.cookies: _*)
      }.recover {
        case e: PageletException =>
          println(s"error $e")
          InternalServerError(s"Error: $e")
      }
    }.getOrElse {
      Future.successful(BadRequest(s"Pagelet '$id' does not exist"))
    }
  }

  def index = Action.async { implicit request =>
    mason.build(plan, Arg("s", "Hello!")).map { result =>

      val hashes = Resource.update(result.js, result.css)
      val js = routes.HomeController.resourceFor(hashes.js.toString)
      val css = routes.HomeController.resourceFor(hashes.css.toString)

      Ok(
        views.html.pageletWrapper("index", js, css)(Html(result.body))
      ).withCookies(result.cookies: _*)
    }.recover {
      case e: PageletException =>
        println(s"error $e")
        InternalServerError(s"Error: $e")
    }
  }

  def pagelet1 = Action.async { implicit request =>
    Future {
      throw new RuntimeException("Oh, an error!")
    }
  }

  def pagelet2(s: String) = Action { implicit request =>
    Ok(s)
    throw new RuntimeException("Ups")
  }

  def fallbackPagelet = Action { implicit request =>
    Ok("fallback!").
      withJavascript(Javascript("hello.js"), Javascript("not found")).
      withCss(Css("hello.css")).withCookies(Cookie("yo", "man"))
  }

  def newRoot = Action {
    Ok("new root!").withCookies(Cookie("yoRoot", "manRoot"))
  }

}
