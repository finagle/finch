package io.finch.endpoint

import cats.data.NonEmptyList
import io.finch._
import io.finch.internal.Rs

private abstract class Param[A](name: String) extends Endpoint[A] {

  protected def missing(input: Input, name: String): Endpoint.Result[A]
  protected def present(input: Input, value: String): Endpoint.Result[A]

  final def apply(input: Input): Endpoint.Result[A] =
    input.request.params.get(name) match {
      case None => missing(input, name)
      case Some(value) => present(input, value)
    }

  final override def item: items.RequestItem = items.ParamItem(name)
  final override def toString: String = s"param($name)"
}

private object Param {
  trait Required { _: Param[String] =>
    protected def missing(input: Input, name: String): Endpoint.Result[String] =
      EndpointResult.Matched(input, Rs.paramNotPresent(name))

    protected def present(input: Input, value: String): Endpoint.Result[String] =
      EndpointResult.Matched(input, Rs.payload(value))
  }

  trait Optional { _: Param[Option[String]] =>
    protected def missing(input: Input, name: String): Endpoint.Result[Option[String]] =
      EndpointResult.Matched(input, Rs.none)

    protected def present(input: Input, value: String): Endpoint.Result[Option[String]] =
      EndpointResult.Matched(input, Rs.payload(Some(value)))
  }

  trait Exists { _: Param[String] =>
    protected def missing(input: Input, name: String): Endpoint.Result[String] =
      EndpointResult.Skipped

    protected def present(input: Input, value: String): Endpoint.Result[String] =
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
  def paramOption(name: String): Endpoint[Option[String]] =
    new Param[Option[String]](name) with Param.Optional

  /**
   * An evaluating [[Endpoint]] that reads a required query-string param `name` from the
   * request or raises an [[Error.NotPresent]] exception when the param is missing; an
   * [[Error.NotValid]] exception is the param is empty.
   */
  def param(name: String): Endpoint[String] =
    new Param[String](name) with Param.Required

  /**
   * A matching [[Endpoint]] that only matches the requests that contain a given query-string
   * param `name`.
   */
  def paramExists(name: String): Endpoint[String] =
    new Param[String](name) with Param.Exists

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
