package io.finch

import java.nio.charset.Charset

import com.twitter.finagle.http.Response
import com.twitter.io.{Buf, Reader}
import com.twitter.util.Future
import io.catbird.util._
import io.finch.internal._
import io.finch.items.RequestItem
import io.iteratee.{Enumeratee, Enumerator, Iteratee}
import shapeless.Witness

/**
  * Streaming module
  */
package object iteratee extends IterateeInstances {

  private[finch] def enumeratorFromReader(reader: Reader, cs: Charset): Enumerator[Future, String] = {
    Enumerator.liftM(reader.read(Int.MaxValue)).flatMap({
      case None => Enumerator.empty[Future, String]
      case Some(buf) => Enumerator.enumOne[Future, String](buf.asString(cs)).append(enumeratorFromReader(reader, cs))
    })
  }

  /**
    * An evaluating [[Endpoint]] that reads a required chunked streaming binary body, interpreted as
    * an `Enumerator[Future, A]`. The returned [[Endpoint]] only matches chunked (streamed) requests.
    */
  def enumeratorBody[A](implicit ee: Enumeratee[Future, String, A]): Endpoint[Enumerator[Future, A]] = {
    new Endpoint[Enumerator[Future, A]] {
      final def apply(input: Input): Endpoint.Result[Enumerator[Future, A]] = {
        if (!input.request.isChunked) {
          EndpointResult.Skipped
        } else {
          val req = input.request
          EndpointResult.Matched(
            input,
            Rerunnable(Output.payload(enumeratorFromReader(req.reader, req.charsetOrUtf8).through(ee)))
          )
        }
      }

      final override def item: RequestItem = items.BodyItem
      final override def toString: String = "asyncJsonBody"
    }
  }

}

trait IterateeInstances {

  implicit def enumeratorToResponse[A, CT <: String](implicit
                                           encode: Encode.Aux[A, CT],
                                           w: Witness.Aux[CT]
                                          ): ToResponse[Enumerator[Future, A]] = {
    ToResponse.instance[Enumerator[Future, A], CT]((enum, cs) => {
      val response = Response()
      response.setChunked(true)
      response.contentType = w.value
      val writer = response.writer
      val iteratee = Iteratee.foldM[Future, Buf, Unit](())((_, buf) => writer.write(buf)).ensure(writer.close())
      enum.map(encode.apply(_, cs)).into(iteratee)
      response
    })
  }

}