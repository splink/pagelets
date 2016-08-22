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

/*
case object Root extends PageletId

case object First extends PageletId

case object Pagelet1 extends PageletId

case object Pagelet2 extends PageletId
*/
@Singleton
class HomeController2 @Inject()(implicit m: Materializer, e: Environment) extends Controller with BricksController {

  val tree = Tree(Root, Seq(
    Tree(First, Seq(
      Leaf(Pagelet1, pagelet1 _).withFallback(fallbackPagelet _),
      Leaf(Pagelet2, pagelet2 _)
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

  def index = RootPagelet(wrapper.apply, resourceRoute)("Index", tree, Arg("s", "Hello!"))

  def pagelet(id: String) = Pagelet(wrapper.apply, resourceRoute)(tree, id)

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
