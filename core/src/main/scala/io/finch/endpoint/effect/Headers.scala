package io.finch.endpoint.effect

import io.finch._
import scala.reflect.ClassTag

trait Headers[F[_]] { self: EffectInstances[F] =>

  /**
    * An evaluating [[Endpoint]] that reads a required HTTP header `name` from the request or raises
    * an [[Error.NotPresent]] exception when the header is missing.
    */
  def header[A](name: String)(implicit d: DecodeEntity[A], tag: ClassTag[A]): Endpoint[F, A] =
    io.finch.endpoint.header[F, A](name)

  /**
    * An evaluating [[Endpoint]] that reads an optional HTTP header `name` from the request into an
    * `Option`.
    */
  def headerOption[A](name: String)(implicit
    d: DecodeEntity[A],
    tag: ClassTag[A]
  ): Endpoint[F, Option[A]] = io.finch.endpoint.headerOption[F, A](name)

}
