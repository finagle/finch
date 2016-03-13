package io.finch

import cats.data.OptionT
import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.http.{ Response, Status, Version }
import com.twitter.io.{ Buf, Reader }
import com.twitter.util.Future
import io.catbird.util._
import io.circe.{ Decoder, Encoder }, io.circe.streaming._
import io.finch.internal.ToResponse
import io.iteratee._

package object iteratee extends Module[Future] {
  final def fromAsyncStream[A](as: AsyncStream[A]): Future[Enumerator[Future, A]] = as.head.map {
    case None => empty[A]
    case Some(h) => generateM((h, as.tail)) {
      case (_, next) => OptionT(next).flatMap(t =>
        OptionT(t.head).map(h => (h, t.tail))
      ).value
    }.map(_._1)
  }

  final def decodeBuf[A: Decoder]: Enumeratee[Future, Buf, A] =
    Enumeratee.map[Future, Buf, String] {
      case Buf.Utf8(s) => s
    }.andThen(stringParser).andThen(decoder[Future, A])

  final def encodeToBuf[A: Encoder]: Enumeratee[Future, A, Buf] =
    Enumeratee.map[Future, A, Buf](a => Buf.Utf8(Encoder[A].apply(a).noSpaces))

  final def foldJson[A: Decoder, B](fold: Iteratee[Future, A, B]): Endpoint[B] =
    asyncBody.mapAsync(fromAsyncStream).mapAsync(_.mapE(decodeBuf[A]).run(fold))

  final def transformJson[A: Decoder, B: Encoder](
    transformation: Enumeratee[Future, A, B]
  ): Endpoint[Enumerator[Future, Buf]] =
    asyncBody.mapAsync(fromAsyncStream).map(
      _.mapE(decodeBuf[A].andThen(transformation).andThen(encodeToBuf))
    )
    
  private[this] def foreach[A](f: A => Unit): Iteratee[Future, A, Unit] = fold(()) {
    case (_, a) => f(a)
  }

  implicit final val enumeratorToResponse: ToResponse[Enumerator[Future, Buf]] =
    ToResponse.instance { enumerator =>
      val writable = Reader.writable()
      enumerator.ensure(writable.close()).run(foreach(writable.write))
      Response(Version.Http11, Status.Ok, writable)
    }
}
