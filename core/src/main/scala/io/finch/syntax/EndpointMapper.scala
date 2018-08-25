package io.finch.syntax

import com.twitter.finagle.http.Method
import io.finch._

class EndpointMapper[F[_], A](m: Method, e: Endpoint[F, A]) extends Endpoint[F, A] { self =>

  /**
    * Maps this endpoint to either `A => Output[B]` or `A => Future[Output[B]]`.
    */
  final def apply(mapper: Mapper[F, A]): Endpoint[F, mapper.Out] = mapper(self)

  final def apply(input: Input): EndpointResult[F, A] =
    if (input.request.method == m) e(input)
    else e(input) match {
      case EndpointResult.Matched(_, _, _) => EndpointResult.NotMatched.MethodNotAllowed(m :: Nil)
      case skipped => skipped
    }

  final override def toString: String = s"${ m.toString.toUpperCase } /${ e.toString }"
}
