package io.finch

import cats.effect.Effect
import com.twitter.finagle.http.Response
import com.twitter.io._
import com.twitter.util.Future
import io.finch.internal._
import io.finch.items.RequestItem
import io.finch.streaming.{DecodeStream, StreamFromReader}
import io.iteratee.{Enumerator, Iteratee}
import shapeless.Witness

/**
  * Iteratee module
  */
package object iteratee extends IterateeInstances {


  /**
    * An evaluating [[Endpoint]] that reads a required chunked streaming binary body, interpreted as
    * an `Enumerator[Future, A]`. The returned [[Endpoint]] only matches chunked (streamed) requests.
    */
  @deprecated("Use Endpoint.streamJsonBody or Endpoint.streamBinaryBody instead", "0.27.0")
  def enumeratorBody[F[_] : Effect, A, CT <: String](implicit
    decode: DecodeStream.Aux[Enumerator, F, A, CT]
  ): Endpoint[F, Enumerator[F, A]] = new Endpoint[F, Enumerator[F, A]] {
    final def apply(input: Input): Endpoint.Result[F, Enumerator[F, A]] = {
      if (!input.request.isChunked) EndpointResult.NotMatched[F]
      else {
        val req = input.request
        EndpointResult.Matched(
          input,
          Trace.empty,
          Effect[F].pure(Output.payload(decode(enumeratorFromReader.apply(req.reader), req.charsetOrUtf8)))
        )
      }
    }

    final override def item: RequestItem = items.BodyItem
    final override def toString: String = "enumeratorBody"
  }

  implicit def enumeratorFromReader[F[_] : Effect](implicit
    toEffect: ToEffect[Future, F]
  ): StreamFromReader[Enumerator, F] =
    StreamFromReader.instance { reader =>
      def rec(reader: Reader[Buf]): Enumerator[F, Buf] = {
        Enumerator.liftM[F, Option[Buf]] {
          Effect[F].defer(toEffect(reader.read()))
        }.flatMap {
          case None => Enumerator.empty[F, Buf]
          case Some(buf) => Enumerator.enumOne[F, Buf](buf).append(rec(reader))
        }
      }
      rec(reader).ensure(Effect[F].delay(reader.discard()))
    }

  implicit def enumeratorToJsonResponse[F[_] : Effect, A](implicit
    e: Encode.Aux[A, Application.Json],
    w: Witness.Aux[Application.Json]
  ): ToResponse.Aux[Enumerator[F, A], Application.Json] = {
    mkToResponse[F, A, Application.Json](delimiter = Some(ToResponse.NewLine))
  }

}

trait IterateeInstances {

  implicit def enumeratorToResponse[F[_] : Effect, A, CT <: String](implicit
    e: Encode.Aux[A, CT],
    w: Witness.Aux[CT],
    toEffect: ToEffect[Future, F]
  ): ToResponse.Aux[Enumerator[F, A], CT] = {
    mkToResponse[F, A, CT](delimiter = None)
  }

  protected def mkToResponse[F[_] : Effect, A, CT <: String](delimiter: Option[Buf])(implicit
    e: Encode.Aux[A, CT],
    w: Witness.Aux[CT],
    toEffect: ToEffect[Future, F]
  ): ToResponse.Aux[Enumerator[F, A], CT] = {
    ToResponse.instance[Enumerator[F, A], CT]((enum, cs) => {
      val response = Response()
      response.setChunked(true)
      response.contentType = w.value
      val writer = response.writer
      val iteratee = Iteratee.foreachM[F, Buf]((buf: Buf) => toEffect(writer.write(delimiter match {
        case Some(d) => buf.concat(d)
        case _ => buf
      })))
      val stream = enum
        .ensure(Effect[F].defer(toEffect(writer.close())))
        .map(e.apply(_, cs))
        .into(iteratee)
      Effect[F].toIO(stream).unsafeRunAsyncAndForget()
      response
    })
  }
}
