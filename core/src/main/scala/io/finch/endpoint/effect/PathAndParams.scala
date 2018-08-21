package io.finch.endpoint.effect

import cats.data.NonEmptyList
import io.finch._
import scala.reflect.ClassTag

trait PathAndParams[F[_]] { self: EffectInstances[F] =>

  /**
    * An evaluating [[Endpoint]] that reads an optional query-string param `name` from the request
    * into an `Option`.
    */
  def paramOption[A](name: String)(implicit
    d: DecodeEntity[A],
    tag: ClassTag[A]
  ): Endpoint[F, Option[A]] = io.finch.endpoint.paramOption[F, A](name)

  /**
    * An evaluating [[Endpoint]] that reads a required query-string param `name` from the
    * request or raises an [[Error.NotPresent]] exception when the param is missing; an
    * [[Error.NotValid]] exception is the param is empty.
    */
  def param[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[F, A] =
    io.finch.endpoint.param[F, A](name)

  /**
    * An evaluating [[Endpoint]] that reads an optional (in a meaning that a resulting
    * `Seq` may be empty) multi-value query-string param `name` from the request into a `Seq`.
    */
  def params[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[F, Seq[A]] =
    io.finch.endpoint.params[F, A](name)

  /**
    * An evaluating [[Endpoint]] that reads a required multi-value query-string param `name`
    * from the request into a `NonEmptyList` or raises a [[Error.NotPresent]] exception
    * when the params are missing or empty.
    */
  def paramsNel[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[F, NonEmptyList[A]] =
    io.finch.endpoint.paramsNel[F, A](name)

}
