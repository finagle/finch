package io.finch.endpoint

import io.catbird.util.Rerunnable
import io.finch._
import io.finch.internal.Rs
import java.util.UUID
import scala.reflect.ClassTag
import shapeless.HNil

private class MatchPath(s: String) extends Endpoint[HNil] {
  final def apply(input: Input): Endpoint.Result[HNil] = input.route match {
    case `s` +: rest => EndpointResult.Matched(input.withRoute(rest), Rs.OutputHNil)
    case _ => EndpointResult.Skipped
  }

  final override def toString: String = s
}

private class ExtractPath[A](implicit d: DecodePath[A], ct: ClassTag[A]) extends Endpoint[A] {
  final def apply(input: Input): Endpoint.Result[A] = input.route match {
    case s +: rest => d(s) match {
      case Some(a) =>
        EndpointResult.Matched(input.withRoute(rest), Rerunnable.const(Output.payload(a)))
      case _ =>
        EndpointResult.Skipped
    }
    case _ => EndpointResult.Skipped
  }

  final override def toString: String = s":${ct.runtimeClass.getSimpleName.toLowerCase}"
}

private class ExtractPaths[A](implicit d: DecodePath[A], ct: ClassTag[A]) extends Endpoint[Seq[A]] {
  final def apply(input: Input): Endpoint.Result[Seq[A]] =
    EndpointResult.Matched(
      input.copy(route = Nil),
      Rerunnable.const(Output.payload(input.route.flatMap(p => d(p).toSeq)))
    )

  final override def toString: String = s":${ct.runtimeClass.getSimpleName.toLowerCase}*"
}

private[finch] trait Paths {

  implicit def stringToPath(s: String): Endpoint[HNil] = path(s)
  implicit def intToPath(i: Int): Endpoint[HNil] = path(i.toString)
  implicit def booleanToPath(b: Boolean): Endpoint[HNil] = path(b.toString)

  /**
   * A matching [[Endpoint]] that reads a value of type `A` (using the implicit
   * [[DecodePath]] instances defined for `A`) from the current path segment.
   */
  def path[A: DecodePath: ClassTag]: Endpoint[A] = new ExtractPath[A]

  /**
   * A matching [[Endpoint]] that reads a tail value `A` (using the implicit
   * [[DecodePath]] instances defined for `A`) from the entire path.
   */
  def paths[A: DecodePath: ClassTag]: Endpoint[Seq[A]] = new ExtractPaths[A]

  /**
   * An [[Endpoint]] that matches a given string.
   */
  def path(s: String): Endpoint[HNil] = new MatchPath(s)

  /**
   * A matching [[Endpoint]] that reads an integer value from the current path segment.
   */
  @deprecated("Use path[Int] instead", "0.16")
  val int: Endpoint[Int] = path[Int]

  /**
   * A matching [[Endpoint]] that reads a long value from the current path segment.
   */
  @deprecated("Use path[Long] instead", "0.16")
  val long: Endpoint[Long] = path[Long]

  /**
   * A matching [[Endpoint]] that reads a string value from the current path segment.
   */
  @deprecated("Use path[String] instead", "0.16")
  val string: Endpoint[String] = path[String]

  /**
   * A matching [[Endpoint]] that reads a boolean value from the current path segment.
   */
  @deprecated("Use path[Boolean] instead", "0.16")
  val boolean: Endpoint[Boolean] = path[Boolean]

  /**
   * A matching [[Endpoint]] that reads an UUID value from the current path segment.
   */
  @deprecated("Use path[UUID] instead", "0.16")
  val uuid: Endpoint[UUID] = path[UUID]

  /**
   * A matching [[Endpoint]] that reads a string tail from the current path segment.
   */
  @deprecated("Use paths[Int] instead", "0.16")
  val ints: Endpoint[Seq[Int]] = paths[Int]

  /**
   * A matching [[Endpoint]] that reads a long tail from the current path segment.
   */
  @deprecated("Use paths[Long] instead", "0.16")
  val longs: Endpoint[Seq[Long]] = paths[Long]

  /**
   * A matching [[Endpoint]] that reads a string tail from the current path segment.
   */
  @deprecated("Use paths[String] instead", "0.16")
  val strings: Endpoint[Seq[String]] = paths[String]

  /**
   * A matching [[Endpoint]] that reads a boolean tail from the current path segment.
   */
  @deprecated("Use paths[Boolean] instead", "0.16")
  val booleans: Endpoint[Seq[Boolean]] = paths[Boolean]

  /**
   * A matching [[Endpoint]] that reads a UUID tail from the current path segment.
   */
  @deprecated("Use paths[UUID] instead", "0.16")
  val uuids: Endpoint[Seq[UUID]] = paths[UUID]

  /**
   * A matching [[Endpoint]] that reads an integer value from the current path segment.
   */
  @deprecated("Use path[Int].withToString(String) instead", "0.16")
  def int(name: String): Endpoint[Int] = int.withToString(name)

  /**
   * A matching [[Endpoint]] that reads a long value from the current path segment.
   */
  @deprecated("Use path[Long].withToString(String) instead", "0.16")
  def long(name: String): Endpoint[Long] = long.withToString(name)

  /**
   * A matching [[Endpoint]] that reads a string value from the current path segment.
   */
  @deprecated("Use path[String].withToString(String) instead", "0.16")
  def string(name: String): Endpoint[String] = string.withToString(name)

  /**
   * A matching [[Endpoint]] that reads a boolean value from the current path segment.
   */
  @deprecated("Use path[Boolean].withToString(String) instead", "0.16")
  def boolean(name: String): Endpoint[Boolean] = boolean.withToString(name)

  /**
   * A matching [[Endpoint]] that reads a UUID value from the current path segment.
   */
  @deprecated("Use path[UUID].withToString(String) instead", "0.16")
  def uuid(name: String): Endpoint[UUID] = uuid.withToString(name)
}
