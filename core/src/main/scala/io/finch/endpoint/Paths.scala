package io.finch.endpoint

import cats.effect.Effect
import io.finch._
import io.netty.handler.codec.http.QueryStringDecoder
import scala.reflect.ClassTag
import shapeless.HNil

private[finch] class Paths[F[_] : Effect] {

  private val EmptyOutput: F[Output[HNil]] = Effect[F].pure(Output.payload(HNil))

  class MatchPath(s: String) extends Endpoint[F, HNil] {
    final def apply(input: Input): EndpointResult[F, HNil] = input.route match {
      case `s` +: rest =>
        EndpointResult.Matched(input.withRoute(rest), Trace.segment(s), EmptyOutput)
      case _ => EndpointResult.NotMatched
    }

    final override def toString: String = s
  }

  class ExtractPath[A](implicit d: DecodePath[A], ct: ClassTag[A]) extends Endpoint[F, A] {

    final def apply(input: Input): EndpointResult[F, A] = input.route match {
      case s +: rest => d(QueryStringDecoder.decodeComponent(s)) match {
        case Some(a) =>
          EndpointResult.Matched(
            input.withRoute(rest),
            Trace.segment(toString),
            Effect[F].pure(Output.payload(a))
          )
        case _ => EndpointResult.NotMatched
      }
      case _ => EndpointResult.NotMatched
    }

    final override lazy val toString: String = s":${ct.runtimeClass.getSimpleName.toLowerCase}"
  }

  class ExtractPaths[A](implicit d: DecodePath[A], ct: ClassTag[A]) extends Endpoint[F, Seq[A]] {
    final def apply(input: Input): EndpointResult[F, Seq[A]] = EndpointResult.Matched(
      input.copy(route = Nil),
      Trace.segment(toString),
      Effect[F].pure(
        Output.payload(input.route.flatMap(p => d(QueryStringDecoder.decodeComponent(p)).toSeq))
      )
    )

    final override lazy val toString: String = s":${ct.runtimeClass.getSimpleName.toLowerCase}*"
  }

}

trait PathsEndpoints[F[_]] {

  implicit def stringToPath(s: String)(implicit effect: Effect[F]): Endpoint[F, HNil] = path(s)
  implicit def intToPath(i: Int)(implicit effect: Effect[F]): Endpoint[F, HNil] = path(i.toString)
  implicit def booleanToPath(b: Boolean)(implicit effect: Effect[F]): Endpoint[F, HNil] = path(b.toString)

  /**
    * A matching [[Endpoint]] that reads a value of type `A` (using the implicit
    * [[DecodePath]] instances defined for `A`) from the current path segment.
    */
  def path[A: DecodePath: ClassTag](implicit effect: Effect[F]): Endpoint[F, A] = {
    val ps = new Paths[F]
    new ps.ExtractPath[A]
  }

  /**
    * A matching [[Endpoint]] that reads a tail value `A` (using the implicit
    * [[DecodePath]] instances defined for `A`) from the entire path.
    */
  def paths[A: DecodePath: ClassTag](implicit effect: Effect[F]): Endpoint[F, Seq[A]] = {
    val ps = new Paths[F]
    new ps.ExtractPaths[A]
  }

  /**
    * An [[Endpoint]] that matches a given string.
    */
  def path(s: String)(implicit effect: Effect[F]): Endpoint[F, HNil] = {
    val ps = new Paths[F]
    new ps.MatchPath(s)
  }

}
