package io.finch.endpoint

import com.twitter.finagle.http.{Method => FinagleMethod}
import io.finch._

private[finch] class Method[F[_], A](m: FinagleMethod, e: Endpoint[F, A]) extends Endpoint.Mappable[F, A] { self =>

  final def apply(input: Input): EndpointResult[F, A] =
    if (input.request.method == m) e(input)
    else e(input) match {
      case EndpointResult.Matched(_, _, _) => EndpointResult.NotMatched.MethodNotAllowed(m :: Nil)
      case skipped => skipped
    }

  final override def toString: String = s"${ m.toString.toUpperCase } /${ e.toString }"
}
