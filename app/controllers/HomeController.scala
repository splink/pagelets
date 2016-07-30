package controllers

import javax.inject._

import akka.stream.Materializer
import org.splink.raven.page.PageFactory
import org.splink.raven.tree.FunctionMacros._
import org.splink.raven.tree._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._

import scala.concurrent.Future

@Singleton
class HomeController @Inject()(implicit mat: Materializer) extends Controller {

  //TODO tree manipulation
  //TODO assets
  //TODO cookies

  case object Root extends PageletId
  case object First extends PageletId
  case object Pagelet1 extends PageletId
  case object Pagelet2 extends PageletId

  val tree = Tree(Root, Seq(
      Tree(First, Seq(
        Leaf(Pagelet1, pagelet1 _).withFallback(pagelet3 _),
        Leaf(Pagelet2, pagelet2 _)
      ))
    ))

  def index = Action.async { implicit request =>
    println(PageFactory.show(tree))
    PageFactory.create(tree, Arg("s", "Hello!")).map { result =>
      Ok(result.body)
    }.recover {
      case t: TypeException =>
        println(s"error $t")
        InternalServerError(s"Error: $t")
    }
  }

  def pagelet1 = Action.async { implicit request =>
    Future { throw new RuntimeException("Oh, an error!") }
  }

  def pagelet2(s: String) = Action { implicit request =>
    Ok(s)
    throw new RuntimeException("Ups")
  }

  def pagelet3 = Action { implicit request =>
    Ok("fallback!")
  }


}