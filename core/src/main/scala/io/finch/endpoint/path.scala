package io.finch.endpoint

import io.catbird.util.Rerunnable
import io.finch._
import io.finch.internal.EmptyOutput
import io.netty.handler.codec.http.QueryStringDecoder
import scala.reflect.ClassTag
import shapeless.HNil

private class MatchPath(s: String) extends Endpoint[HNil] {
  final def apply(input: Input): Endpoint.Result[HNil] = input.route match {
    case `s` +: rest => EndpointResult.Matched(input.withRoute(rest), EmptyOutput)
    case _ => EndpointResult.NotMatched
  }

  final override def toString: String = s
}

private class ExtractPath[A](implicit d: DecodePath[A], ct: ClassTag[A]) extends Endpoint[A] {
  final def apply(input: Input): Endpoint.Result[A] = input.route match {
    case s +: rest => d(QueryStringDecoder.decodeComponent(s)) match {
      case Some(a) =>
        EndpointResult.Matched(input.withRoute(rest), Rerunnable.const(Output.payload(a)))
      case _ =>
        EndpointResult.NotMatched
    }
    case _ => EndpointResult.NotMatched
  }

  final override def toString: String = s":${ct.runtimeClass.getSimpleName.toLowerCase}"
}

private class ExtractPaths[A](implicit d: DecodePath[A], ct: ClassTag[A]) extends Endpoint[Seq[A]] {
  final def apply(input: Input): Endpoint.Result[Seq[A]] = EndpointResult.Matched(
    input.copy(route = Nil),
    Rerunnable.const(
      Output.payload(input.route.flatMap(p => d(QueryStringDecoder.decodeComponent(p)).toSeq))
    )
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
}
