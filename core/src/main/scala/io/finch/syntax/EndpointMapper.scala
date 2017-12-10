package io.finch.syntax

import com.twitter.finagle.http.Method
import io.finch._

class EndpointMapper[A](m: Method, e: Endpoint[A]) extends Endpoint[A] { self =>

  /**
   * Maps this endpoint to either `A => Output[B]` or `A => Future[Output[B]]`.
   */
  final def apply(mapper: Mapper[A]): Endpoint[mapper.Out] = mapper(self)

  final def apply(input: Input): Endpoint.Result[A] =
    if (input.request.method == m) e(input)
    else EndpointResult.Skipped

  final override def toString: String = s"${ m.toString.toUpperCase } /${ e.toString }"

  final def meta: Endpoint.Meta = EndpointMetadata.Method(m, e.meta)
}
