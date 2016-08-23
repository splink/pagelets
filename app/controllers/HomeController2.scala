package controllers

import javax.inject._

import akka.stream.Materializer
import org.splink.raven.FunctionMacros._
import org.splink.raven.BrickResult._
import org.splink.raven.TwirlConversions._
import org.splink.raven._
import play.api.Environment
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import views.html.wrapper

import scala.concurrent.Future

@Singleton
class HomeController2 @Inject()(implicit m: Materializer, e: Environment) extends Controller with BricksController {

  val plan = Tree('root, Seq(
    Tree('first, Seq(
      Leaf('brick1, pagelet1 _).withFallback(fallbackPagelet _),
      Leaf('brick2, pagelet2 _)
    ), results => combine(results)(views.html.test.apply)
    )))

  /*.
      skip(Pagelet2).
      replace(Pagelet1, Leaf(Pagelet2, pagelet2 _)).
      replace(Root, Leaf(Pagelet1, newRoot _))
      */
//TODO error template
  //TODO withJavascriptTop

  val resourceRoute: String => Call = routes.HomeController2.resourceFor(_)

  def index = Wall(wrapper.apply, resourceRoute)("Index", plan, Arg("s", "Hello!"))

  def part(id: String) = WallPart(wrapper.apply, resourceRoute)(plan, id)

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
      withCss(Css("hello.css")).withCookies(Cookie("yo", "man2"))
  }

  def newRoot = Action {
    Ok("new root!").withCookies(Cookie("yoRoot", "manRoot"))
  }
}
