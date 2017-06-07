package io.finch

import com.twitter.finagle.http.{Cookie, Request}
import io.catbird.util.Rerunnable
import io.finch.endpoint._
import io.finch.internal._
import shapeless._

/**
 * A collection of [[Endpoint]] combinators.
 */
trait Endpoints extends Bodies with Paths with FileUploads with Headers with ParamAndParams {

  type Endpoint0 = Endpoint[HNil]
  type Endpoint2[A, B] = Endpoint[A :: B :: HNil]
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

  private[this] def requestCookie(cookie: String)(req: Request): Option[Cookie] =
    req.cookies.get(cookie)

  private[this] def option[A](item: items.RequestItem)(f: Request => A): Endpoint[A] =
    Endpoint.embed(item)(input =>
      EndpointResult.Matched(input, Rerunnable(Output.payload(f(input.request))))
    )

  /**
   * A root [[Endpoint]] that always matches and extracts the current request.
   */
  val root: Endpoint[Request] = option(items.MultipleItems)(identity)

  /**
   * An evaluating [[Endpoint]] that reads an optional HTTP cookie from the request into an
   * `Option`.
   */
  def cookieOption(name: String): Endpoint[Option[Cookie]] =
    option(items.CookieItem(name))(requestCookie(name))

  /**
   * An evaluating [[Endpoint]] that reads a required cookie from the request or raises an
   * [[Error.NotPresent]] exception when the cookie is missing.
   */
  def cookie(name: String): Endpoint[Cookie] = cookieOption(name).failIfNone
}
