package controllers

import javax.inject._

import akka.stream.Materializer
import org.splink.raven.TwirlConversions._
import org.splink.raven._
import play.api.Environment
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import views.html.{error, wrapper}

import scala.concurrent.Future

@Singleton
class HomeController @Inject()(c: BricksController)(implicit m: Materializer, e: Environment) extends Controller  {
  import c._

  def plan(r: RequestHeader) = Tree('root, Seq(
    Tree('first, Seq(
      Leaf('brick1, pagelet1 _).withFallback(fallbackPagelet _),
      Tree('sub, Seq(
        Leaf('brick2, pagelet2 _),
        Leaf('more, more _)
      ))
    ), results => combine(results)(views.html.test.apply)
    ))).//skip('brick1).
      replace('brick2, Leaf('yo, yo _))


  val mainTemplate = wrapper(routes.HomeController.resourceFor) _
  val errorTemplate = error(_)

  def resourceFor(fingerprint: String) = ResourceAction(fingerprint)

  def index = PageAction(errorTemplate)("Index", plan, Arg("s", "Hello!")) { (request, page) =>
    println(visualize(plan(request)))
    mainTemplate(page)
  }

  def part(id: Symbol) = PagePartAction(errorTemplate)(plan, id) { (request, page) =>
    mainTemplate(page)
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
    Ok("<b>fallback!</b>").
      withJavascript(Javascript("hello.js"), Javascript("not found")).
      withCss(Css("hello.css")).
      withCookies(Cookie("yo", "man2")).
      withMetaTags(MetaTag("one", "oneContent"), MetaTag("two", "twoContent")).
      withJavascriptTop(Javascript("hello2.js"))
  }

  def more = Action { implicit request =>
    Ok(views.html.simple("more...")).
      withCss(Css("hello.css")).
      withMetaTags(MetaTag("one", "oneContent"), MetaTag("three", "threeContent"))
  }

  def yo = Action {
    Ok("yo!").withCookies(Cookie("yoRoot", "manRoot"))
  }
}
