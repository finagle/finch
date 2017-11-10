package io.finch.syntax.scala

import scala.concurrent.{Future => ScalaFuture}
import scala.util.{Failure, Success}

import com.twitter.util.{Future, Promise}
import io.finch.syntax.ToTwitterFuture

trait ScalaToTwitterFuture {
  implicit val scalaToTwitterFuture: ToTwitterFuture[ScalaFuture] =
    new ToTwitterFuture[ScalaFuture] {
      def apply[A](f: ScalaFuture[A]): Future[A] = {
        val p = Promise[A]()

        f.onComplete {
          case Success(a) => p.setValue(a)
          case Failure(t) => p.setException(t)
        }(CallingThreadExecutionContext)

        p
      }
    }
}
