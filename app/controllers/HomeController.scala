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

  val tree = Tree(PageletId("root"),
    Seq(
      Tree(PageletId("first"), Seq(
        Leaf(PageletId("pagelet1"), pagelet1 _),
        Leaf(PageletId("pagelet2"), pagelet2 _)
      ))
    ))

  def index = Action.async { implicit request =>
    PageFactory.create(tree, Arg("s", "Hello!")).map { result =>
      Ok(result.body)
    }.recover {
      case t: TypeException =>
        println(s"error $t")
        InternalServerError(s"Error: $t")
    }
  }

  def pagelet1: Action[AnyContent] = Action.async { request =>
    Future.successful(Ok("ok"))
  }

  def pagelet2(s: String): Action[AnyContent] = Action { request =>
    Ok(s)
  }

}
