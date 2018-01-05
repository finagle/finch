package io.finch.syntax

import com.twitter.util.{Future, Promise}
import scala.concurrent.{ExecutionContext, Future => ScalaFuture}
import scala.util.{Failure, Success}

object scalafutures extends EndpointMappers {

  private[scalafutures] object CallingThreadExecutionContext extends ExecutionContext {
    def execute(runnable: Runnable): Unit = runnable.run()

    def reportFailure(throwable: Throwable): Unit = throwable.printStackTrace()
  }

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
