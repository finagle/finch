package io.finch

import com.twitter.util.{Future, Promise}
import scala.concurrent.{ExecutionContext, Future => ScalaFuture}
import scala.util.{Failure, Success, Try}

/**
 * Enables Sinatra-like syntax extensions for endpoints.
 */
package object syntax extends EndpointMappers {

  /**
   * Enables Scala Futures support for Finch syntax.
   */
  object scalaFutures {

    private val executeNow = new ExecutionContext {
      def execute(runnable: Runnable): Unit = runnable.run()
      def reportFailure(throwable: Throwable): Unit = throwable.printStackTrace()
    }

    implicit val scalaToTwitterFuture: ToTwitterFuture[ScalaFuture] =
      new ToTwitterFuture[ScalaFuture] {
        def apply[A](f: ScalaFuture[A]): Future[A] = {
          val p = new Promise[A] with (Try[A] => Unit) {
            def apply(ta: Try[A]): Unit = ta match {
              case Success(a) => setValue(a)
              case Failure(t) => setException(t)
            }
          }

          f.onComplete(p)(executeNow)

          p
        }
      }
  }
}
