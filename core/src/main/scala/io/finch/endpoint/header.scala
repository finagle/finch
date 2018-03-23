package io.finch.endpoint

import scala.reflect.ClassTag

import cats.Id
import com.twitter.util.{Return, Throw}
import io.finch._
import io.finch.internal._
import io.finch.items._

private abstract class Header[F[_], A](name: String, d: DecodeEntity[A], tag: ClassTag[A]) extends Endpoint[F[A]] {
  self =>

  protected def missing(input: Input, name: String): Endpoint.Result[F[A]]
  protected def present(input: Input, value: A): Endpoint.Result[F[A]]

  final def apply(input: Input): Endpoint.Result[F[A]] =
    input.request.headerMap.getOrNull(name) match {
      case null => missing(input, name)
      case value => d(value) match {
        case Return(r) => present(input, r)
        case Throw(e) => EndpointResult.Matched(input, Rs.headerNotParsed(name, tag, e))
      }
    }

  final override def item: RequestItem = items.HeaderItem(name)
  final override def toString: String = s"header($name)"
}

private object Header {
  trait Required[A] { _: Header[Id, A] =>
    protected def missing(input: Input, name: String): Endpoint.Result[A] =
      EndpointResult.Matched(input, Rs.headerNotPresent(name))

    protected def present(input: Input, value: A): Endpoint.Result[A] =
      EndpointResult.Matched(input, Rs.payload(value))
  }

  trait Optional[A] { _: Header[Option,A] =>
    protected def missing(input: Input, name: String): Endpoint.Result[Option[A]] =
      EndpointResult.Matched(input, Rs.none)

    protected def present(input: Input, value: A): Endpoint.Result[Option[A]] =
      EndpointResult.Matched(input, Rs.payload(Some(value)))
  }

  trait Exists[A] { _: Header[Id, A] =>
    protected def missing(input: Input, name: String): Endpoint.Result[A] =
      EndpointResult.NotMatched

    protected def present(input: Input, value: A): Endpoint.Result[A] =
      EndpointResult.Matched(input, Rs.payload(value))
  }
}

private[finch] trait Headers {

  /**
   * An evaluating [[Endpoint]] that reads a required HTTP header `name` from the request or raises
   * an [[Error.NotPresent]] exception when the header is missing.
   */
  def header[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[A] =
    new Header[Id, A](name, d, tag) with Header.Required[A]

  /**
   * An evaluating [[Endpoint]] that reads an optional HTTP header `name` from the request into an
   * `Option`.
   */
  def headerOption[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[Option[A]] =
    new Header[Option, A](name, d, tag) with Header.Optional[A]

  /**
   * A matching [[Endpoint]] that only matches the requests that contain a given header `name`.
   */
  def headerExists[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[A] =
    new Header[Id, A](name, d, tag) with Header.Exists[A]
}
