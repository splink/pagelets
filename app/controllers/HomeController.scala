package controllers

import javax.inject._

import akka.stream.Materializer
import org.splink.raven.FunctionMacros._
import org.splink.raven.TwirlConversions._

import org.splink.raven.PageletResult._
import org.splink.raven._
import play.api.Environment
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._

import scala.concurrent.Future

@Singleton
class HomeController @Inject()(implicit m: Materializer, e: Environment) extends Controller {

  //TODO assets

  case object Root extends PageletId
  case object First extends PageletId
  case object Pagelet1 extends PageletId
  case object Pagelet2 extends PageletId

  val tree = Tree(Root, Seq(
    Tree(First, Seq(
      Leaf(Pagelet1, pagelet1 _).withFallback(fallbackPagelet _),
      Leaf(Pagelet2, pagelet2 _)
    ), results => combine(results)(views.html.test.apply)
    )))/*.
    skip(Pagelet2).
    replace(Pagelet1, Leaf(Pagelet2, pagelet2 _)).
    replace(Root, Leaf(Pagelet1, newRoot _))
    */

  def resourceFor(key: String) = Action {
    Resources.contentFor(key).map { content =>
      Ok(content.body).as(content.mimeType)
    }.getOrElse {
      BadRequest
    }
  }

  def index = Action.async { implicit request =>
    println(PageFactory.show(tree))
    PageFactory.create(tree, Arg("s", "Hello!")).map { result =>

      println(Resources.update(result.js, result.css))

      Ok(result).withCookies(result.cookies: _*)
    }.recover {
      case t: TypeException =>
        println(s"error $t")
        InternalServerError(s"Error: $t")
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
