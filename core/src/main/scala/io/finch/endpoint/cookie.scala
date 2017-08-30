package io.finch.endpoint

import com.twitter.finagle.http.{Cookie => FinagleCookie}
import io.catbird.util.Rerunnable
import io.finch._
import io.finch.internal._

private abstract class Cookie[A](name: String) extends Endpoint[A] {

  protected def missing(name: String): Rerunnable[Output[A]]
  protected def present(value: FinagleCookie): Rerunnable[Output[A]]

  def apply(input: Input): Endpoint.Result[A] = input.request.cookies.get(name) match {
    case None => EndpointResult.Matched(input, missing(name))
    case Some(value) => EndpointResult.Matched(input, present(value))
  }

  final override def item: items.RequestItem = items.CookieItem(name)
  final override def toString: String = s"cookie($name)"
}

private object Cookie {
  trait Optional { _: Cookie[Option[FinagleCookie]] =>
    protected def missing(name: String): Rerunnable[Output[Option[FinagleCookie]]] =
      Rs.none
    protected def present(value: FinagleCookie): Rerunnable[Output[Option[FinagleCookie]]] =
      Rs.payload(Some(value))
  }

  trait Required { _: Cookie[FinagleCookie] =>
    protected def missing(name: String): Rerunnable[Output[FinagleCookie]] =
      Rs.cookieNotPresent(name)
    protected def present(value: FinagleCookie): Rerunnable[Output[FinagleCookie]] =
      Rs.payload(value)
  }
}


private[finch] trait Cookies {

  /**
   * An evaluating [[Endpoint]] that reads an optional HTTP cookie from the request into an
   * `Option`.
   */
  def cookieOption(name: String): Endpoint[Option[FinagleCookie]] =
    new Cookie[Option[FinagleCookie]](name) with Cookie.Optional

  /**
   * An evaluating [[Endpoint]] that reads a required cookie from the request or raises an
   * [[Error.NotPresent]] exception when the cookie is missing.
   */
  def cookie(name: String): Endpoint[FinagleCookie] =
    new Cookie[FinagleCookie](name) with Cookie.Required
}
