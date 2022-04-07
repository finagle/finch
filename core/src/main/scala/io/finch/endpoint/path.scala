package io.finch.endpoint

import scala.reflect.ClassTag

import cats.Applicative
import io.finch._
import io.netty.handler.codec.http.QueryStringDecoder
import shapeless.HNil

private[finch] class MatchPath[F[_]](s: String)(implicit
    F: Applicative[F]
) extends Endpoint[F, HNil] {
  final def apply(input: Input): EndpointResult[F, HNil] = input.route match {
    case `s` :: rest =>
      EndpointResult.Matched(
        input.withRoute(rest),
        Trace.segment(s),
        F.pure(Output.HNil)
      )
    case _ => EndpointResult.NotMatched[F]
  }

  final override def toString: String = s
}

private[finch] class ExtractPath[F[_], A](implicit
    d: DecodePath[A],
    ct: ClassTag[A],
    F: Applicative[F]
) extends Endpoint[F, A] {
  final def apply(input: Input): EndpointResult[F, A] = input.route match {
    case s :: rest =>
      d(QueryStringDecoder.decodeComponent(s)) match {
        case Some(a) =>
          EndpointResult.Matched(
            input.withRoute(rest),
            Trace.segment(toString),
            F.pure(Output.payload(a))
          )
        case _ => EndpointResult.NotMatched[F]
      }
    case _ => EndpointResult.NotMatched[F]
  }

  final override lazy val toString: String = s":${ct.runtimeClass.getSimpleName.toLowerCase}"
}

private[finch] class ExtractPaths[F[_], A](implicit
    d: DecodePath[A],
    ct: ClassTag[A],
    F: Applicative[F]
) extends Endpoint[F, List[A]] {
  final def apply(input: Input): EndpointResult[F, List[A]] = EndpointResult.Matched(
    input.copy(route = Nil),
    Trace.segment(toString),
    F.pure(Output.payload(input.route.flatMap(p => d(QueryStringDecoder.decodeComponent(p)).toList)))
  )

  final override lazy val toString: String = s":${ct.runtimeClass.getSimpleName.toLowerCase}*"
}
