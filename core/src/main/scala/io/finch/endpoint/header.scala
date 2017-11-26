package io.finch.endpoint

import scala.reflect.ClassTag

import com.twitter.util.{Return, Throw}
import io.finch._
import io.finch.internal._
import io.finch.items._

private abstract class Header[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]) extends Endpoint[A] {
  self =>

  protected def missing(input: Input, name: String): Endpoint.Result[A]
  protected def present(input: Input, value: A): Endpoint.Result[A]

  final def apply(input: Input): Endpoint.Result[A] =
    input.request.headerMap.getOrNull(name) match {
      case null => missing(input, name)
      case value => d(value) match {
        case Return(r) => present(input, r)
        case Throw(e) => notParsed(self, input, e, tag)
      }
    }

  final override def item: RequestItem = items.HeaderItem(name)
  final override def toString: String = s"header($name)"
}

private object Header {
  trait Required[A] { _: Header[A] =>
    protected def missing(input: Input, name: String): Endpoint.Result[A] =
      EndpointResult.Matched(input, Rs.headerNotPresent(name))

    protected def present(input: Input, value: A): Endpoint.Result[A] =
      EndpointResult.Matched(input, Rs.payload(value))
  }

  trait Optional[A] { _: Header[Option[A]] =>
    protected def missing(input: Input, name: String): Endpoint.Result[Option[A]] =
      EndpointResult.Matched(input, Rs.none)

    protected def present(input: Input, value: Option[A]): Endpoint.Result[Option[A]] =
      EndpointResult.Matched(input, Rs.payload(value))
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
  def header[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[A] =
    new Header[A](name) with Header.Required[A]

  /**
   * An evaluating [[Endpoint]] that reads an optional HTTP header `name` from the request into an
   * `Option`.
   */
  def headerOption[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[Option[A]] =
    new Header[Option[A]](name) with Header.Optional[A]

  /**
   * A matching [[Endpoint]] that only matches the requests that contain a given header `name`.
   */
  def headerExists(name: String): Endpoint[String] =
    new Header[String](name) with Header.Exists
}
