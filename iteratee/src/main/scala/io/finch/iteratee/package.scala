package io.finch

import com.twitter.finagle.http.Response
import com.twitter.io.{Buf, Reader}
import com.twitter.util.Future
import io.catbird.util._
import io.finch.internal._
import io.finch.items.RequestItem
import io.finch.iteratee.AsyncEncode
import io.iteratee.Enumerator
import io.iteratee.twitter.FutureModule
import shapeless.Witness

/**
  * Iteratee module
  */
package object iteratee extends IterateeInstances {

  import syntax._

  private[finch] def enumeratorFromReader(reader: Reader): Enumerator[Future, Buf] = {
    reader.read(Int.MaxValue).intoEnumerator.flatMap({
      case None => empty[Buf]
      case Some(buf) => enumOne(buf).append(enumeratorFromReader(reader))
    })
  }

  /**
    * An evaluating [[Endpoint]] that reads a required chunked streaming binary body, interpreted as
    * an `Enumerator[Future, A]`. The returned [[Endpoint]] only matches chunked (streamed) requests.
    */
  def enumeratorBody[A, CT <: String](implicit decode: AsyncDecode.Aux[A, CT]): Endpoint[Enumerator[Future, A]] = {
    new Endpoint[Enumerator[Future, A]] {
      final def apply(input: Input): Endpoint.Result[Enumerator[Future, A]] = {
        if (!input.request.isChunked) {
          EndpointResult.Skipped
        } else {
          val req = input.request
          EndpointResult.Matched(
            input,
            Rerunnable(Output.payload(decode(enumeratorFromReader(req.reader), req.charsetOrUtf8)))
          )
        }
      }

      final override def item: RequestItem = items.BodyItem
      final override def toString: String = "asyncJsonBody"
    }
  }

  /**
    * An evaluating [[Endpoint]] that reads a required chunked streaming JSON body, interpreted as
    * an `Enumerator[Future, A]`. The returned [[Endpoint]] only matches chunked (streamed) requests.
    */
  def enumeratorJsonBody[A](implicit ad: AsyncDecode.Aux[A, Application.Json]): Endpoint[Enumerator[Future, A]] =
    enumeratorBody[A, Application.Json]

}

trait IterateeInstances extends FutureModule  {

  implicit def enumeratorToResponse[A, CT <: String](implicit
                                           ae: AsyncEncode.Aux[A, CT],
                                           w: Witness.Aux[CT]
                                          ): ToResponse.Aux[Enumerator[Future, A], CT] = {
    ToResponse.instance[Enumerator[Future, A], CT]((enum, cs) => {
      val response = Response()
      response.setChunked(true)
      response.contentType = w.value
      val writer = response.writer
      val iteratee = foreachM((buf: Buf) => writer.write(buf))
      ae(enum, cs).into(iteratee).ensure(writer.close())
      response
    })
  }

}
