package io.finch.endpoint.effect

import com.twitter.finagle.http.Cookie
import io.finch._

trait Cookies[F[_]] { self: EffectInstances[F] =>

  /**
    * An evaluating [[Endpoint]] that reads an optional HTTP cookie from the request into an
    * `Option`.
    */
  def cookieOption(name: String): Endpoint[F, Option[Cookie]] = io.finch.endpoint.cookieOption[F](name)

  /**
    * An evaluating [[Endpoint]] that reads a required cookie from the request or raises an
    * [[Error.NotPresent]] exception when the cookie is missing.
    */
  def cookie(name: String): Endpoint[F, Cookie] = io.finch.endpoint.cookie(name)

}
