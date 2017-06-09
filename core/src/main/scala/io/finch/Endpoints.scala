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
  with FileUploads
  with Headers
  with ParamAndParams
  with Cookies {

  @deprecated("Use Endpoint[HNil] instead", "0.15")
  type Endpoint0 = Endpoint[HNil]
  @deprecated("Use Endpoint[A :: B :: HNil] instead", "0.15")
  type Endpoint2[A, B] = Endpoint[A :: B :: HNil]
  @deprecated("Use Endpoint[A :: B :: C :: HNil] instead", "0.15")
  type Endpoint3[A, B, C] = Endpoint[A :: B :: C :: HNil]

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
