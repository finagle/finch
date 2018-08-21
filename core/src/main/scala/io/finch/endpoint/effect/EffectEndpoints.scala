package io.finch.endpoint.effect

import com.twitter.finagle.http.Request
import io.finch._
import shapeless.HNil

trait EffectEndpoints[F[_]] extends Bodies[F]
  with Cookies[F]
  with FileUploadsAndAttributes[F]
  with Headers[F]
  with PathAndParams[F]
  with Paths[F] { self: EffectInstances[F] =>

  /**
    * An [[Endpoint]] that skips all path segments.
    */
  val * : Endpoint[F, HNil] = io.finch.endpoint.*[F]

  /**
    * An identity [[Endpoint]].
    */
  val / : Endpoint[F, HNil] = io.finch.endpoint./[F]

  /**
    * A root [[Endpoint]] that always matches and extracts the current request.
    */
  def root: Endpoint[F, Request] = io.finch.endpoint.root[F]

}
