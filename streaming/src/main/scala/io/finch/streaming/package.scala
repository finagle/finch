package io.finch

import com.twitter.io.{Buf, Reader}
import com.twitter.util.Future
import io.catbird.util._
import io.finch.Endpoint.Result
import io.finch.items.RequestItem
import io.iteratee.{Enumeratee, Enumerator}

/**
  * Streaming module
  */
package object streaming {

  private[finch] def enumeratorFromReader(reader: Reader): Enumerator[Future, Buf] = {
    Enumerator.liftM(reader.read(Int.MaxValue)).flatMap({
      case None => Enumerator.empty[Future, Buf]
      case Some(buf) => Enumerator.enumOne[Future, Buf](buf).append(enumeratorFromReader(reader))
    })
  }

  /**
    * An evaluating [[Endpoint]] that reads a required chunked streaming binary body, interpreted as
    * an `Enumerator[Future, Buf]`. The returned [[Endpoint]] only matches chunked (streamed) requests.
    */
  def asyncBufBody: Endpoint[Enumerator[Future, Buf]] = new Endpoint[Enumerator[Future, Buf]] {
    final def apply(input: Input): Endpoint.Result[Enumerator[Future, Buf]] =
      if (!input.request.isChunked) EndpointResult.Skipped
      else EndpointResult.Matched(input,
        Rerunnable(Output.payload(enumeratorFromReader(input.request.reader))))

    final override def item: RequestItem = items.BodyItem
    final override def toString: String = "asyncBufBody"
  }

  /**
    * An evaluating [[Endpoint]] that reads a required chunked streaming binary body, interpreted as
    * an `Enumerator[Future, A]`. The returned [[Endpoint]] only matches chunked (streamed) requests.
    */
  def asyncJsonBody[A](implicit ee: Enumeratee[Future, Buf, A]): Endpoint[Enumerator[Future, A]] = {
    new Endpoint[Enumerator[Future, A]] {
      final def apply(input: Input): Result[Enumerator[Future, A]] = asyncBufBody.map(_.through(ee))(input)

      final override def item: RequestItem = items.BodyItem
      final override def toString: String = "asyncJsonBody"
    }
  }

}
