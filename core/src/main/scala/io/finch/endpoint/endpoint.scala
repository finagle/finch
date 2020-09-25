package io.finch

import java.io.InputStream

import cats.Applicative
import cats.effect.{ContextShift, Resource, Sync}
import cats.syntax.all._
import com.twitter.finagle.http.{Method => FinagleMethod}
import com.twitter.io.Buf
import shapeless.HNil

package object endpoint {

  private[finch] class FromInputStream[F[_]](stream: Resource[F, InputStream])(implicit
      F: Sync[F],
      S: ContextShift[F]
  ) extends Endpoint[F, Buf] {

    private def readLoop(left: Buf, stream: InputStream): F[Buf] = F.suspend {
      val buffer = new Array[Byte](1024)
      val n = stream.read(buffer)
      if (n == -1) F.pure(left)
      else readLoop(left.concat(Buf.ByteArray.Owned(buffer, 0, n)), stream)
    }

    final def apply(input: Input): Endpoint.Result[F, Buf] =
      EndpointResult.Matched(
        input,
        Trace.empty,
        stream.use(s => S.shift.flatMap(_ => readLoop(Buf.Empty, s)).map(buf => Output.payload(buf)))
      )
  }

  private[finch] class Asset[F[_]](path: String)(implicit F: Applicative[F]) extends Endpoint[F, HNil] {
    final def apply(input: Input): Endpoint.Result[F, HNil] = {
      val req = input.request
      if (req.method != FinagleMethod.Get || req.path != path) EndpointResult.NotMatched[F]
      else
        EndpointResult.Matched(
          input.withRoute(Nil),
          Trace.fromRoute(input.route),
          F.pure(Output.HNil)
        )
    }

    final override def toString: String = s"GET /$path"
  }
}
