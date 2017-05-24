package io.finch.endpoint

import io.finch._
import io.finch.internal._
import io.finch.items._

private abstract class Header[A](name: String) extends Endpoint[A] {

  protected def missing(input: Input, name: String): Endpoint.Result[A]
  protected def present(input: Input, value: String): Endpoint.Result[A]

  final def apply(input: Input): Endpoint.Result[A] =
    input.request.headerMap.getOrNull(name) match {
      case null => missing(input, name)
      case value => present(input, value)
    }

  final override def item: RequestItem = items.HeaderItem(name)
  final override def toString: String = s"header($name)"
}

private object Header {
  trait Required { _: Header[String] =>
    protected def missing(input: Input, name: String): Endpoint.Result[String] =
      EndpointResult.Matched(input, Rs.headerNotPresent(name))

    protected def present(input: Input, value: String): Endpoint.Result[String] =
      EndpointResult.Matched(input, Rs.payload(value))
  }

  trait Optional { _: Header[Option[String]] =>
    protected def missing(input: Input, name: String): Endpoint.Result[Option[String]] =
      EndpointResult.Matched(input, Rs.none)

    protected def present(input: Input, value: String): Endpoint.Result[Option[String]] =
      EndpointResult.Matched(input, Rs.payload(Some(value)))
  }

  trait Exists { _: Header[String] =>
    protected def missing(input: Input, name: String): Endpoint.Result[String] =
      EndpointResult.Skipped

    protected def present(input: Input, value: String): Endpoint.Result[String] =
      EndpointResult.Matched(input, Rs.payload(value))
  }
}

private[finch] trait Headers {

  /**
   * An evaluating [[Endpoint]] that reads a required HTTP header `name` from the request or raises
   * an [[Error.NotPresent]] exception when the header is missing.
   */
  def header(name: String): Endpoint[String] =
    new Header[String](name) with Header.Required

  /**
   * An evaluating [[Endpoint]] that reads an optional HTTP header `name` from the request into an
   * `Option`.
   */
  def headerOption(name: String): Endpoint[Option[String]] =
    new Header[Option[String]](name) with Header.Optional

  /**
   * A matching [[Endpoint]] that only matches the requests that contain a given header `name`.
   */
  def headerExists(name: String): Endpoint[String] =
    new Header[String](name) with Header.Exists
}
