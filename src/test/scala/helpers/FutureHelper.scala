package helpers

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait FutureHelper {

  implicit class FutureOps[T](f: Future[T]) {
    def futureValue(implicit timeout: FiniteDuration = 1.second) = Await.result(f, timeout)

    def futureTry(implicit timeout: FiniteDuration = 1.second) = Await.ready(f, timeout).value.get
  }

}
