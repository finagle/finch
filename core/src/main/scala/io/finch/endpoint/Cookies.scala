package io.finch.endpoint

import cats.effect.Effect
import com.twitter.finagle.http.{Cookie => FinagleCookie}
import io.finch._

private[finch] class Cookies[F[_] : Effect] {

  abstract class Cookie[A](name: String) extends Endpoint[F, A] {

    protected def missing(name: String): F[Output[A]]
    protected def present(value: FinagleCookie): F[Output[A]]

    def apply(input: Input): EndpointResult[F, A] = {
      val output = Effect[F].suspend {
        input.request.cookies.get(name) match {
          case None => missing(name)
          case Some(value) => present(value)
        }
      }

      EndpointResult.Matched(input, Trace.empty, output)
    }

    final override def item: items.RequestItem = items.CookieItem(name)
    final override def toString: String = s"cookie($name)"
  }

  object Cookie {

    private val noneInstance: F[Output[Option[Nothing]]] = Effect[F].pure(Output.None)
    private def none[A]: F[Output[Option[A]]] = noneInstance.asInstanceOf[F[Output[Option[A]]]]

    trait Optional { _: Cookie[Option[FinagleCookie]] =>
      protected def missing(name: String): F[Output[Option[FinagleCookie]]] = none[FinagleCookie]
      protected def present(value: FinagleCookie): F[Output[Option[FinagleCookie]]] =
        Effect[F].pure(Output.payload(Some(value)))
    }

    trait Required { _: Cookie[FinagleCookie] =>
      protected def missing(name: String): F[Output[FinagleCookie]] =
        Effect[F].raiseError(Error.NotPresent(items.CookieItem(name)))
      protected def present(value: FinagleCookie): F[Output[FinagleCookie]] =
        Effect[F].pure(Output.payload(value))
    }
  }

}

trait CookieEndpoints[F[_]] {

  /**
    * An evaluating [[Endpoint]] that reads an optional HTTP cookie from the request into an
    * `Option`.
    */
  def cookieOption(name: String)(implicit effect: Effect[F]): Endpoint[F, Option[FinagleCookie]] = {
    val cookies = new Cookies[F]
    new cookies.Cookie[Option[FinagleCookie]](name) with cookies.Cookie.Optional
  }

  /**
    * An evaluating [[Endpoint]] that reads a required cookie from the request or raises an
    * [[Error.NotPresent]] exception when the cookie is missing.
    */
  def cookie(name: String)(implicit effect: Effect[F]): Endpoint[F, FinagleCookie] = {
    val cookies = new Cookies[F]
    new cookies.Cookie[FinagleCookie](name) with cookies.Cookie.Required
  }

}
