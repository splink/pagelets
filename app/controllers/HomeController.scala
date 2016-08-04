package controllers

import javax.inject._

import akka.stream.Materializer
import org.splink.raven.FunctionMacros._
import org.splink.raven.TwirlConversions._

import org.splink.raven.PageletResult._
import org.splink.raven._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._

import scala.concurrent.Future

@Singleton
class HomeController @Inject()(implicit mat: Materializer) extends Controller {

  //TODO assets
  //TODO cookies

  case object Root extends PageletId
  case object First extends PageletId
  case object Pagelet1 extends PageletId
  case object Pagelet2 extends PageletId

  val tree = Tree(Root, Seq(
    Tree(First, Seq(
      Leaf(Pagelet1, pagelet1 _).withFallback(fallbackPagelet _),
      Leaf(Pagelet2, pagelet2 _)
    ), results => combine(results)(views.html.test.apply)
    ))).skip(Pagelet2).replace(Pagelet1, Leaf(Pagelet2, pagelet2 _)).replace(Root, Leaf(Pagelet1, newRoot _))

  def index = Action.async { implicit request =>
    println(PageFactory.show(tree))
    PageFactory.create(tree, Arg("s", "Hello!")).map { result =>
      Ok(result)
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
    Ok("fallback!").withJavascript(Javascript("js"), Javascript("more js")).withCss(Css("css"))
  }

  def newRoot = Action {
    Ok("new root!")
  }

}
