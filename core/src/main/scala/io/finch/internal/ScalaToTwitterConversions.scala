package io.finch.internal

import com.twitter.util._

private[finch] object ScalaToTwitterConversions {
  import scala.concurrent.ExecutionContext

  implicit def scalaToTwitterTry[T](t: scala.util.Try[T]): Try[T] = t match {
    case scala.util.Success(r) => Return(r)
    case scala.util.Failure(ex) => Throw(ex)
  }

  implicit class ScalaTwitterConvertionsImplicits[T](f: scala.concurrent.Future[T]) {
    def asTwitterFuture(implicit ec: ExecutionContext): Future[T] = {
      val promise = Promise[T]()
      f.onComplete(promise update _)
      promise
    }
  }

}
