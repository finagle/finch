package io.finch.syntax

import com.twitter.finagle.http.Method
import io.finch._

class EndpointMapper[A](m: Method, e: Endpoint[A]) extends Endpoint[A] { self =>

  /**
   * Maps this endpoint to either `A => Output[B]` or `A => Future[Output[B]]`.
   */
  final def apply[F](f: => F)(implicit mapper: Mapper[F, A]): Endpoint[mapper.Out] = mapper(f, self)

  final def apply(input: Input): Endpoint.Result[A] =
    if (input.request.method == m) e(input)
    else EndpointResult.Skipped

  final override def toString: String = s"${ m.toString.toUpperCase } /${ e.toString }"
}
