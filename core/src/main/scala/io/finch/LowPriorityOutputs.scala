package io.finch

import com.twitter.util.Future

trait LowPriorityOutputs {
  import Endpoint.Output

  // Implicitly converts an `Endpoint.Output[A] to `Future[Endpoint.Output[A]]`.
  implicit def outputToFutureOutput[A](o: Output[A]): Future[Output[A]] =
    Future.value(o)
}
