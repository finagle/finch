package io.finch.endpoint

import cats.data.NonEmptyList
import com.twitter.util.{Return, Throw}
import io.finch._
import io.finch.internal.{ResolveDecodeEntity, Rs}
import scala.reflect.ClassTag

private abstract class Param[A, B](
    name: String,
    decode: DecodeEntity[A],
    ct: ClassTag[A]) extends Endpoint[B] {

  protected def missing(input: Input, name: String): Endpoint.Result[B]
  protected def present(input: Input, value: A): Endpoint.Result[B]

  final def apply(input: Input): Endpoint.Result[B] =
    input.request.params.get(name) match {
      case None => missing(input, name)
      case Some(raw) => decode(raw) match {
        case Return(value) => present(input, value)
        case Throw(t) => EndpointResult.Matched(input, Rs.paramNotParsed(name, ct, t))
      }
    }

  final override def item: items.RequestItem = items.ParamItem(name)
  final override def toString: String = s"param($name)"
}

private object Param {
  trait Required[A] { _: Param[A, A] =>
    protected def missing(input: Input, name: String): Endpoint.Result[A] =
      EndpointResult.Matched(input, Rs.paramNotPresent(name))

    protected def present(input: Input, value: A): Endpoint.Result[A] =
      EndpointResult.Matched(input, Rs.payload(value))
  }

  trait Optional[A] { _: Param[A, Option[A]] =>
    protected def missing(input: Input, name: String): Endpoint.Result[Option[A]] =
      EndpointResult.Matched(input, Rs.none)

    protected def present(input: Input, value: A): Endpoint.Result[Option[A]] =
      EndpointResult.Matched(input, Rs.payload(Some(value)))
  }

  trait Exists[A] { _: Param[A, A] =>
    protected def missing(input: Input, name: String): Endpoint.Result[A] =
      EndpointResult.Skipped

    protected def present(input: Input, value: A): Endpoint.Result[A] =
      EndpointResult.Matched(input, Rs.payload(value))
  }
}

private abstract class Params[A](name: String) extends Endpoint[A] {

  protected def missing(input: Input, name: String): Endpoint.Result[A]
  protected def present(input: Input, value: Iterable[String]): Endpoint.Result[A]

  def apply(input: Input): Endpoint.Result[A] = input.request.params.getAll(name) match {
    case value if value.isEmpty => missing(input, name)
    case value => present(input, value)
  }
  final override def item: items.RequestItem = items.ParamItem(name)
  final override def toString: String = s"params($name)"
}

private object Params {
  trait AllowEmpty { _: Params[Seq[String]] =>
    protected def missing(input: Input, name: String): Endpoint.Result[Seq[String]] =
      EndpointResult.Matched(input, Rs.nil)

    protected def present(input: Input, value: Iterable[String]): Endpoint.Result[Seq[String]] =
      EndpointResult.Matched(input, Rs.payload(value.toSeq))
  }

  trait NonEmpty { _: Params[NonEmptyList[String]] =>
    protected def missing(input: Input, name: String): Endpoint.Result[NonEmptyList[String]] =
      EndpointResult.Matched(input, Rs.paramNotPresent(name))

    protected def present(
      input: Input,
      value: Iterable[String]
    ): Endpoint.Result[NonEmptyList[String]] = EndpointResult.Matched(
      input, Rs.payload(NonEmptyList.fromListUnsafe(value.toList))
    )
  }
}

private[finch] trait ParamAndParams {
  /**
   * An evaluating [[Endpoint]] that reads an optional query-string param `name` from the request
   * into an `Option`.
   */
  def paramOption[A](name: String)(implicit r: ResolveDecodeEntity[A]): Endpoint[Option[r.Out]] =
    new Param[r.Out, Option[r.Out]](name, r.decodeEntity, r.classTag) with Param.Optional[r.Out]

  /**
   * An evaluating [[Endpoint]] that reads a required query-string param `name` from the
   * request or raises an [[Error.NotPresent]] exception when the param is missing; an
   * [[Error.NotValid]] exception is the param is empty.
   */
  def param[A](name: String)(implicit r: ResolveDecodeEntity[A]): Endpoint[r.Out] =
    new Param[r.Out, r.Out](name, r.decodeEntity, r.classTag) with Param.Required[r.Out]

  /**
   * A matching [[Endpoint]] that only matches the requests that contain a given query-string
   * param `name`.
   */
  def paramExists[A](name: String)(implicit r: ResolveDecodeEntity[A]): Endpoint[r.Out] =
    new Param[r.Out, r.Out](name, r.decodeEntity, r.classTag) with Param.Exists[r.Out]

  /**
   * An evaluating [[Endpoint]] that reads an optional (in a meaning that a resulting
   * `Seq` may be empty) multi-value query-string param `name` from the request into a `Seq`.
   */
  def params(name: String): Endpoint[Seq[String]] =
    new Params[Seq[String]](name) with Params.AllowEmpty

  /**
   * An evaluating [[Endpoint]] that reads a required multi-value query-string param `name`
   * from the request into a `NonEmptyList` or raises a [[Error.NotPresent]] exception
   * when the params are missing or empty.
   */
  def paramsNel(name: String): Endpoint[NonEmptyList[String]] =
    new Params[NonEmptyList[String]](name) with Params.NonEmpty
}
