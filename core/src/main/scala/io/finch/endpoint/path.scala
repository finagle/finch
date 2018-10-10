package io.finch.endpoint

import cats.effect.Effect
import io.finch._
import io.netty.handler.codec.http.QueryStringDecoder
import scala.reflect.ClassTag
import shapeless.HNil

private[finch] class MatchPath[F[_]](s: String)(implicit
  F: Effect[F]
) extends Endpoint[F, HNil] {
  final def apply(input: Input): EndpointResult[F, HNil] = input.route match {
    case `s` +: rest =>
      EndpointResult.Matched(
        input.withRoute(rest),
        Trace.segment(s),
        F.pure(Output.payload(HNil))
      )
    case _ => EndpointResult.NotMatched
  }

  final override def toString: String = s
}

private[finch] class ExtractPath[F[_], A](implicit
  d: DecodePath[A],
  ct: ClassTag[A],
  F: Effect[F]
) extends Endpoint[F, A] {
  final def apply(input: Input): EndpointResult[F, A] = input.route match {
    case s +: rest => d(QueryStringDecoder.decodeComponent(s)) match {
      case Some(a) =>
        EndpointResult.Matched(
          input.withRoute(rest),
          Trace.segment(toString),
          F.pure(Output.payload(a))
        )
      case _ => EndpointResult.NotMatched
    }
    case _ => EndpointResult.NotMatched
  }

  final override lazy val toString: String = s":${ct.runtimeClass.getSimpleName.toLowerCase}"
}

private[finch] class ExtractPaths[F[_], A](implicit
  d: DecodePath[A],
  ct: ClassTag[A],
  F: Effect[F]
) extends Endpoint[F, Seq[A]] {
  final def apply(input: Input): EndpointResult[F, Seq[A]] = EndpointResult.Matched(
    input.copy(route = Nil),
    Trace.segment(toString),
    F.pure(Output.payload(input.route.flatMap(p => d(QueryStringDecoder.decodeComponent(p)).toSeq)))
  )

  final override lazy val toString: String = s":${ct.runtimeClass.getSimpleName.toLowerCase}*"
}
