package helpers

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait FutureHelper {

  implicit class FutureOps[T](f: Future[T]) {
    def futureValue(implicit timeout: FiniteDuration = 1.second) = Await.result(f, timeout)

    def futureTry(implicit timeout: FiniteDuration = 1.second) = Await.ready(f, timeout).value.get
  }

  implicit class SourceConsumer(src: Source[ByteString, _])(implicit m: Materializer) {
    def consume = src.runFold("")((acc, next) => acc + next.utf8String).futureValue
  }

}
