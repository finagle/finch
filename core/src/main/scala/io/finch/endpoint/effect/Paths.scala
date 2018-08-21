package io.finch.endpoint.effect

import io.finch._
import scala.reflect.ClassTag
import shapeless.HNil

trait Paths[F[_]] { self: EffectInstances[F] =>

  implicit def stringToPath(s: String): Endpoint[F, HNil] = io.finch.endpoint.path(s)
  implicit def intToPath(i: Int): Endpoint[F, HNil] = io.finch.endpoint.path(i.toString)
  implicit def booleanToPath(b: Boolean): Endpoint[F, HNil] = io.finch.endpoint.path(b.toString)

  /**
    * A matching [[Endpoint]] that reads a value of type `A` (using the implicit
    * [[DecodePath]] instances defined for `A`) from the current path segment.
    */
  def path[A: DecodePath: ClassTag]: Endpoint[F, A] = io.finch.endpoint.path[F, A]

  /**
    * A matching [[Endpoint]] that reads a tail value `A` (using the implicit
    * [[DecodePath]] instances defined for `A`) from the entire path.
    */
  def paths[A: DecodePath: ClassTag]: Endpoint[F, Seq[A]] = io.finch.endpoint.paths[F, A]

  /**
    * An [[Endpoint]] that matches a given string.
    */
  def path(s: String): Endpoint[F, HNil] = io.finch.endpoint.path[F](s)

}
