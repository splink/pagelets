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


case object Root extends PageletId

case object First extends PageletId

case object Pagelet1 extends PageletId

case object Pagelet2 extends PageletId

@Singleton
class HomeController @Inject()(implicit m: Materializer, e: Environment) extends Controller {


  val tree = Tree(Root, Seq(
    Tree(First, Seq(
      Leaf(Pagelet1, pagelet1 _).withFallback(fallbackPagelet _),
      Leaf(Pagelet2, pagelet2 _).withFallback(fallbackPagelet _)
    ), results => combine(results)(views.html.test.apply)
    )))
  /*.
      skip(Pagelet2).
      replace(Pagelet1, Leaf(Pagelet2, pagelet2 _)).
      replace(Root, Leaf(Pagelet1, newRoot _))
      */

  def resourceFor(fingerprint: String) = ResourceAction(fingerprint)

  def pagelet(id: String) = Action.async { implicit request =>
    import TreeImplicits._
    tree.find(id).map { pagelet =>
      val args = request.queryString.map { case (key, values) =>
        Arg(key, values.head)
      }.toSeq

      PageFactory.create(pagelet, args: _*).map { result =>
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
    PageFactory.create(tree, Arg("s", "Hello!")).map { result =>

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
