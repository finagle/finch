package io.finch

import arrows.twitter.Task
import cats.Eval
import com.twitter.finagle.http.Response
import com.twitter.io._
import com.twitter.util.Future
import io.catbird.util._
import io.finch.internal._
import io.finch.items.RequestItem
import io.iteratee.{Enumerator, Iteratee}
import io.iteratee.twitter.FutureModule
import shapeless.Witness

/**
  * Iteratee module
  */
package object iteratee extends IterateeInstances {

  import syntax._

  private[finch] def enumeratorFromReader(reader: Reader[Buf]): Enumerator[Future, Buf] = {
    def rec(reader: Reader[Buf]): Enumerator[Future, Buf] = {
      reader.read(Int.MaxValue).intoEnumerator.flatMap {
        case None => empty[Buf]
        case Some(buf) => enumOne(buf).append(rec(reader))
      }
    }
    rec(reader).ensureEval(Eval.later(Future.value(reader.discard())))
  }

  /**
    * An evaluating [[Endpoint]] that reads a required chunked streaming binary body, interpreted as
    * an `Enumerator[Future, A]`. The returned [[Endpoint]] only matches chunked (streamed) requests.
    */
  def enumeratorBody[A, CT <: String](implicit decode: Enumerate.Aux[A, CT]): Endpoint[Enumerator[Future, A]] = {
    new Endpoint[Enumerator[Future, A]] {
      final def apply(input: Input): Endpoint.Result[Enumerator[Future, A]] = {
        if (!input.request.isChunked) EndpointResult.NotMatched
        else {
          val req = input.request
          EndpointResult.Matched(
            input,
            Trace.empty,
            Task(Output.payload(decode(enumeratorFromReader(req.reader), req.charsetOrUtf8)))
          )
        }
      }

      final override def item: RequestItem = items.BodyItem
      final override def toString: String = "enumeratorBody"
    }
  }

  /**
    * An evaluating [[Endpoint]] that reads a required chunked streaming JSON body, interpreted as
    * an `Enumerator[Future, A]`. The returned [[Endpoint]] only matches chunked (streamed) requests.
    */
  def enumeratorJsonBody[A](implicit ad: Enumerate.Aux[A, Application.Json]): Endpoint[Enumerator[Future, A]] =
    enumeratorBody[A, Application.Json].withToString("enumeratorJsonBody")

}

trait IterateeInstances extends LowPriorityInstances {

  implicit def enumeratorToJsonResponse[A](implicit
    e: Encode.Aux[A, Application.Json],
    w: Witness.Aux[Application.Json]
  ): ToResponse.Aux[Enumerator[Future, A], Application.Json] = {
    withCustomIteratee[A, Application.Json](writer =>
      foreachM((buf: Buf) => writer.write(buf.concat(ToResponse.NewLine)))
    )
  }
}

trait LowPriorityInstances extends FutureModule {
  implicit def enumeratorToResponse[A, CT <: String](implicit
    e: Encode.Aux[A, CT],
    w: Witness.Aux[CT]
  ): ToResponse.Aux[Enumerator[Future, A], CT] = {
    withCustomIteratee(writer => foreachM((buf: Buf) => writer.write(buf)))
  }

  protected def withCustomIteratee[A, CT <: String](iteratee: Writer[Buf] => Iteratee[Future, Buf, Unit])(implicit
    e: Encode.Aux[A, CT],
    w: Witness.Aux[CT]
  ): ToResponse.Aux[Enumerator[Future, A], CT] = {
    ToResponse.instance[Enumerator[Future, A], CT]((enum, cs) => {
      val response = Response()
      response.setChunked(true)
      response.contentType = w.value
      val writer = response.writer
      enum.ensureEval(Eval.later(writer.close())).map(e.apply(_, cs)).into(iteratee(writer))
      response
    })
  }
}
