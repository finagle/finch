package io.finch.endpoint

import com.twitter.finagle.http.{Cookie => FinagleCookie}
import com.twitter.util.Future
import io.catbird.util.Rerunnable
import io.finch._

private abstract class Cookie[A](name: String) extends Endpoint[A] {

  protected def missing(name: String): Future[Output[A]]
  protected def present(value: FinagleCookie): Future[Output[A]]

  def apply(input: Input): Endpoint.Result[A] = {
    val output = Rerunnable.fromFuture {
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

private object Cookie {

  private val noneInstance: Future[Output[Option[Nothing]]] = Future.value(Output.None)
  private def none[A] = noneInstance.asInstanceOf[Future[Output[Option[A]]]]

  trait Optional { _: Cookie[Option[FinagleCookie]] =>
    protected def missing(name: String): Future[Output[Option[FinagleCookie]]] = none[FinagleCookie]
    protected def present(value: FinagleCookie): Future[Output[Option[FinagleCookie]]] =
      Future.value(Output.payload(Some(value)))
  }

  trait Required { _: Cookie[FinagleCookie] =>
    protected def missing(name: String): Future[Output[FinagleCookie]] =
      Future.exception(Error.NotPresent(items.CookieItem(name)))
    protected def present(value: FinagleCookie): Future[Output[FinagleCookie]] =
      Future.value(Output.payload(value))
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
