package io.finch

import cats.effect.Effect
import com.twitter.finagle.http.Request
import shapeless.HNil

package object endpoint extends BodyEndpoints
  with PathsEndpoints
  with HeaderEndpoints
  with ParamAndParamsEndpoints
  with CookieEndpoints
  with FileUploadsAndAttributesEndpoints {

  private def emptyOutput[F[_] : Effect]: F[Output[HNil]] = Effect[F].pure(Output.payload(HNil))

  /**
    * An [[Endpoint]] that skips all path segments.
    */
  def *[F[_] : Effect]: Endpoint[F, HNil] = {
    new Endpoint[F, HNil] {
      private val empty = emptyOutput[F]
      final def apply(input: Input): Endpoint.Result[F, HNil] =
        EndpointResult.Matched(input.copy(route = Nil), Trace.empty, empty)

      final override def toString: String = "*"
    }
  }

  /**
    * An identity [[Endpoint]].
    */
  def /[F[_] : Effect]: Endpoint[F, HNil] = new Endpoint[F, HNil] {
    private val empty = emptyOutput[F]
    final def apply(input: Input): Endpoint.Result[F, HNil] =
      EndpointResult.Matched(input, Trace.empty, empty)

    final override def toString: String = ""
  }

  /**
    * A root [[Endpoint]] that always matches and extracts the current request.
    */
  def root[F[_] : Effect]: Endpoint[F, Request] = new Endpoint[F, Request] {
    final def apply(input: Input): Endpoint.Result[F, Request] =
      EndpointResult.Matched(input, Trace.empty, Effect[F].delay(Output.payload(input.request)))

    final override def toString: String = "root"
  }

}
