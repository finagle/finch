package io.finch.endpoint

import scala.reflect.ClassTag

import cats.Id
import cats.data.NonEmptyList
import com.twitter.util._
import io.finch._
import io.finch.internal.Rs

private abstract class Param[F[_], A](name: String, d: DecodeEntity[A], tag: ClassTag[A]) extends Endpoint[F[A]] {
  self =>

  protected def missing(input: Input, name: String): Endpoint.Result[F[A]]
  protected def present(input: Input, value: A): Endpoint.Result[F[A]]

  final def apply(input: Input): Endpoint.Result[F[A]] =
    input.request.params.get(name) match {
      case None => missing(input, name)
      case Some(value) =>
        d(value) match {
          case Return(r) => present(input, r)
          case Throw(e) => EndpointResult.Matched(input, Rs.paramNotParsed(name, tag, e))
        }
    }

  final override def item: items.RequestItem = items.ParamItem(name)
  final override def toString: String = s"param($name)"


}

private object Param {
  trait Required[A] { _: Param[Id, A] =>
    protected def missing(input: Input, name: String): Endpoint.Result[A] =
      EndpointResult.Matched(input, Rs.paramNotPresent(name))

    protected def present(input: Input, value: A): Endpoint.Result[A] =
      EndpointResult.Matched(input, Rs.payload(value))
  }

  trait Optional[A] { _: Param[Option, A] =>
    protected def missing(input: Input, name: String): Endpoint.Result[Option[A]] =
      EndpointResult.Matched(input, Rs.none)

    protected def present(input: Input, value: A): Endpoint.Result[Option[A]] =
      EndpointResult.Matched(input, Rs.payload(Some(value)))
  }

  trait Exists[A] { _: Param[Id, A] =>
    protected def missing(input: Input, name: String): Endpoint.Result[A] =
      EndpointResult.NotMatched

    protected def present(input: Input, value: A): Endpoint.Result[A] =
      EndpointResult.Matched(input, Rs.payload(value))
  }
}

private abstract class Params[F[_], A](name: String, d: DecodeEntity[A], tag: ClassTag[A]) extends Endpoint[F[A]] {

  protected def missing(input: Input, name: String): Endpoint.Result[F[A]]
  protected def present(input: Input, value: Iterable[A]): Endpoint.Result[F[A]]

  def apply(input: Input): Endpoint.Result[F[A]] = input.request.params.getAll(name) match {
    case value if value.isEmpty => missing(input, name)
    case value =>
      val decoded = value.map(d.apply).toList
      val errors = decoded.collect {
        case Throw(t) => t
      }
      NonEmptyList.fromList(errors) match {
        case None => present(input, decoded.map(_.get()))
        case Some(es) => EndpointResult.Matched(input, Rs.paramsNotParsed(name, tag, es))
      }
  }
  final override def item: items.RequestItem = items.ParamItem(name)
  final override def toString: String = s"params($name)"
}

private object Params {
  trait AllowEmpty[A] { _: Params[Seq, A] =>
    protected def missing(input: Input, name: String): Endpoint.Result[Seq[A]] =
      EndpointResult.Matched(input, Rs.nil)

    protected def present(input: Input, value: Iterable[A]): Endpoint.Result[Seq[A]] =
      EndpointResult.Matched(input, Rs.payload(value.toSeq))
  }

  trait NonEmpty[A] { _: Params[NonEmptyList, A] =>
    protected def missing(input: Input, name: String): Endpoint.Result[NonEmptyList[A]] =
      EndpointResult.Matched(input, Rs.paramNotPresent(name))

    protected def present(
      input: Input,
      value: Iterable[A]
    ): Endpoint.Result[NonEmptyList[A]] = EndpointResult.Matched(
      input, Rs.payload(NonEmptyList.fromListUnsafe(value.toList))
    )
  }
}

private[finch] trait ParamAndParams {
  /**
   * An evaluating [[Endpoint]] that reads an optional query-string param `name` from the request
   * into an `Option`.
   */
  def paramOption[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[Option[A]] =
    new Param[Option, A](name, d, tag) with Param.Optional[A]

  /**
   * An evaluating [[Endpoint]] that reads a required query-string param `name` from the
   * request or raises an [[Error.NotPresent]] exception when the param is missing; an
   * [[Error.NotValid]] exception is the param is empty.
   */
  def param[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[A] =
    new Param[Id, A](name, d, tag) with Param.Required[A]

  /**
   * A matching [[Endpoint]] that only matches the requests that contain a given query-string
   * param `name`.
   */
  def paramExists[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[A] =
    new Param[Id, A](name, d, tag) with Param.Exists[A]

  /**
   * An evaluating [[Endpoint]] that reads an optional (in a meaning that a resulting
   * `Seq` may be empty) multi-value query-string param `name` from the request into a `Seq`.
   */
  def params[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[Seq[A]] =
    new Params[Seq, A](name, d, tag) with Params.AllowEmpty[A]

  /**
   * An evaluating [[Endpoint]] that reads a required multi-value query-string param `name`
   * from the request into a `NonEmptyList` or raises a [[Error.NotPresent]] exception
   * when the params are missing or empty.
   */
  def paramsNel[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[NonEmptyList[A]] =
    new Params[NonEmptyList, A](name, d, tag) with Params.NonEmpty[A]
}
