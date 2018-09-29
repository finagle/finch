package io.finch

import cats.Applicative
import cats.effect.Effect
import com.twitter.finagle.http.Request
import io.finch.endpoint._
import shapeless.HNil

/**
 * A collection of [[Endpoint]] combinators.
 */
trait Endpoints[F[_]] extends BodyEndpoints[F]
  with PathsEndpoints[F]
  with HeaderEndpoints[F]
  with ParamAndParamsEndpoints[F]
  with CookieEndpoints[F]
  with FileUploadsAndAttributesEndpoints[F] {

  def emptyOutput(implicit ap: Applicative[F]): F[Output[HNil]] = Applicative[F].pure(Output.payload(HNil))

  /**
   * An [[Endpoint]] that skips all path segments.
   */
  def *(implicit ap: Applicative[F]): Endpoint[F, HNil] = {
    new Endpoint[F, HNil] {
      private val empty = emptyOutput
      final def apply(input: Input): Endpoint.Result[F, HNil] =
        EndpointResult.Matched(input.copy(route = Nil), Trace.empty, empty)

      final override def toString: String = "*"
    }
  }

  /**
   * An identity [[Endpoint]].
   */
  def /(implicit ap: Applicative[F]): Endpoint[F, HNil] = new Endpoint[F, HNil] {
    private val empty = emptyOutput
    final def apply(input: Input): Endpoint.Result[F, HNil] =
      EndpointResult.Matched(input, Trace.empty, empty)

    final override def toString: String = ""
  }

  /**
   * A root [[Endpoint]] that always matches and extracts the current request.
   */
  def root(implicit effect: Effect[F]): Endpoint[F, Request] = new Endpoint[F, Request] {
    final def apply(input: Input): Endpoint.Result[F, Request] =
      EndpointResult.Matched(input, Trace.empty, Effect[F].delay(Output.payload(input.request)))

    final override def toString: String = "root"
  }
}
