package io.finch

import cats.effect.{Effect, IO}
import com.twitter.finagle.http.Response
import com.twitter.io._
import com.twitter.util.Future
import io.finch.internal._
import io.finch.items.RequestItem
import io.iteratee.{Enumerator, Iteratee}
import shapeless.Witness

/**
  * Iteratee module
  */
package object iteratee extends IterateeInstances {


  private[finch] def enumeratorFromReader[F[_] : Effect](reader: Reader[Buf]): Enumerator[F, Buf] = {
    def rec(reader: Reader[Buf]): Enumerator[F, Buf] = {
      Enumerator.liftM[F, Option[Buf]] {
        futureToEffect(reader.read(Int.MaxValue))
      }.flatMap {
        case None => Enumerator.empty[F, Buf]
        case Some(buf) => Enumerator.enumOne[F, Buf](buf).append(rec(reader))
      }
    }
    rec(reader).ensure(Effect[F].delay(reader.discard()))
  }

  /**
    * An evaluating [[Endpoint]] that reads a required chunked streaming binary body, interpreted as
    * an `Enumerator[Future, A]`. The returned [[Endpoint]] only matches chunked (streamed) requests.
    */
  def enumeratorBody[F[_] : Effect, A, CT <: String](implicit
    decode: Enumerate.Aux[F, A, CT]
  ): Endpoint[F, Enumerator[F, A]] = new Endpoint[F, Enumerator[F, A]] {
      final def apply(input: Input): Endpoint.Result[F, Enumerator[F, A]] = {
        if (!input.request.isChunked) EndpointResult.NotMatched
        else {
          val req = input.request
          EndpointResult.Matched(
            input,
            Trace.empty,
            Effect[F].pure(Output.payload(decode(enumeratorFromReader(req.reader), req.charsetOrUtf8)))
          )
        }
      }

      final override def item: RequestItem = items.BodyItem
      final override def toString: String = "enumeratorBody"
  }

  /**
    * An evaluating [[Endpoint]] that reads a required chunked streaming JSON body, interpreted as
    * an `Enumerator[Future, A]`. The returned [[Endpoint]] only matches chunked (streamed) requests.
    */
  def enumeratorJsonBody[F[_] : Effect, A](implicit
    ad: Enumerate.Aux[F, A, Application.Json]
  ): Endpoint[F, Enumerator[F, A]] = enumeratorBody[F, A, Application.Json].withToString("enumeratorJsonBody")

}

trait IterateeInstances extends LowPriorityInstances {

  implicit def enumeratorToJsonResponse[F[_] : Effect, A](implicit
    e: Encode.Aux[A, Application.Json],
    w: Witness.Aux[Application.Json]
  ): ToResponse.Aux[Enumerator[F, A], Application.Json] = {
    withCustomIteratee[F, A, Application.Json](writer =>
      Iteratee.foreachM[F, Buf]((buf: Buf) => futureToEffect(writer.write(buf.concat(ToResponse.NewLine))))
    )
  }
}

trait LowPriorityInstances {

  protected def futureToEffect[F[_] : Effect, A](future: => Future[A]): F[A] = {
    Effect[F].async[A](cb => {
      future
        .onFailure(t => cb(Left(t)))
        .onSuccess(b => cb(Right(b)))
    })
  }

  implicit def enumeratorToResponse[F[_] : Effect, A, CT <: String](implicit
    e: Encode.Aux[A, CT],
    w: Witness.Aux[CT]
  ): ToResponse.Aux[Enumerator[F, A], CT] = {
    withCustomIteratee(writer => Iteratee.foreachM[F, Buf]((buf: Buf) => futureToEffect(writer.write(buf))))
  }

  protected def withCustomIteratee[F[_] : Effect, A, CT <: String]
  (iteratee: Writer[Buf] => Iteratee[F, Buf, Unit])(implicit
    e: Encode.Aux[A, CT],
    w: Witness.Aux[CT]
  ): ToResponse.Aux[Enumerator[F, A], CT] = {
    ToResponse.instance[Enumerator[F, A], CT]((enum, cs) => {
      val response = Response()
      response.setChunked(true)
      response.contentType = w.value
      val writer = response.writer
      val stream = {
        enum.ensure(Effect[F].suspend(futureToEffect(writer.close()))).map(e.apply(_, cs)).into(iteratee(writer))
      }
      Effect[F].runAsync(stream) {
        _ => IO.pure(())
      }.unsafeRunSync()
      response
    })
  }
}
