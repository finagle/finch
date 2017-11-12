package io.finch

import com.twitter.finagle.http.Request
import io.finch.endpoint._
import io.finch.internal._
import shapeless._

/**
 * A collection of [[Endpoint]] combinators.
 */
trait Endpoints extends Bodies
  with Paths
  with Headers
  with ParamAndParams
  with Cookies
  with FileUploadsAndAttributes {

  /**
   * An [[Endpoint]] that skips all path segments.
   */
  object * extends Endpoint[HNil] {
    final def apply(input: Input): Endpoint.Result[HNil] =
      EndpointResult.Matched(input.copy(route = Nil), Rs.OutputHNil)

    final override def toString: String = "*"
  }

  /**
   * An identity [[Endpoint]].
   */
  object / extends Endpoint[HNil] {
    final def apply(input: Input): Endpoint.Result[HNil] =
      EndpointResult.Matched(input, Rs.OutputHNil)

    final override def toString: String = ""
  }

  /**
   * A root [[Endpoint]] that always matches and extracts the current request.
   */
  object root extends Endpoint[Request] {
    final def apply(input: Input): Endpoint.Result[Request] =
      EndpointResult.Matched(input, Rs.payload(input.request))

    final override def toString: String = "root"
  }
}
