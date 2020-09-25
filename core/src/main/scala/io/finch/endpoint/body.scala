package io.finch.endpoint

import java.nio.charset.{Charset, StandardCharsets}

import scala.reflect.ClassTag

import cats.effect.Sync
import com.twitter.io.{Buf, Reader}
import io.finch._
import io.finch.internal._
import io.finch.items._

abstract private[finch] class FullBody[F[_], A] extends Endpoint[F, A] {

  protected def F: Sync[F]
  protected def missing: F[Output[A]]
  protected def present(contentType: String, content: Buf, cs: Charset): F[Output[A]]

  final def apply(input: Input): EndpointResult[F, A] =
    if (input.request.isChunked) EndpointResult.NotMatched[F]
    else {
      val output = F.suspend {
        val contentLength = input.request.contentLengthOrNull
        if (contentLength == null || contentLength == "0") missing
        else
          present(
            input.request.mediaTypeOrEmpty,
            input.request.content,
            input.request.charsetOrUtf8
          )
      }

      EndpointResult.Matched(input, Trace.empty, output)
    }

  final override def item: RequestItem = items.BodyItem
}

private[finch] object FullBody {

  trait PreparedBody[F[_], A, B] { _: FullBody[F, B] =>
    protected def prepare(a: A): B
  }

  trait Required[F[_], A] extends PreparedBody[F, A, A] { _: FullBody[F, A] =>
    protected def prepare(a: A): A = a
    protected def missing: F[Output[A]] = F.raiseError(Error.NotPresent(items.BodyItem))
  }

  trait Optional[F[_], A] extends PreparedBody[F, A, Option[A]] { _: FullBody[F, Option[A]] =>
    protected def prepare(a: A): Option[A] = Some(a)
    protected def missing: F[Output[Option[A]]] = F.pure(Output.None)
  }
}

abstract private[finch] class Body[F[_], A, B, CT](implicit
    dd: Decode.Dispatchable[A, CT],
    ct: ClassTag[A],
    protected val F: Sync[F]
) extends FullBody[F, B]
    with FullBody.PreparedBody[F, A, B] {

  protected def present(contentType: String, content: Buf, cs: Charset): F[Output[B]] =
    dd(contentType, content, cs) match {
      case Right(s) => F.pure(Output.payload(prepare(s)))
      case Left(e)  => F.raiseError(Error.NotParsed(items.BodyItem, ct, e))
    }

  final override def toString: String = "body"
}

abstract private[finch] class BinaryBody[F[_], A](implicit protected val F: Sync[F]) extends FullBody[F, A] with FullBody.PreparedBody[F, Array[Byte], A] {

  protected def present(contentType: String, content: Buf, cs: Charset): F[Output[A]] =
    F.pure(Output.payload(prepare(content.asByteArray)))

  final override def toString: String = "binaryBody"
}

abstract private[finch] class StringBody[F[_], A](implicit protected val F: Sync[F]) extends FullBody[F, A] with FullBody.PreparedBody[F, String, A] {

  protected def present(contentType: String, content: Buf, cs: Charset): F[Output[A]] =
    F.pure(Output.payload(prepare(content.asString(cs))))

  final override def toString: String = "stringBody"
}

abstract private[finch] class ChunkedBody[F[_], S[_[_], _], A] extends Endpoint[F, S[F, A]] {

  protected def F: Sync[F]
  protected def prepare(r: Reader[Buf], cs: Charset): Output[S[F, A]]

  final def apply(input: Input): EndpointResult[F, S[F, A]] =
    if (!input.request.isChunked) EndpointResult.NotMatched[F]
    else
      EndpointResult.Matched(
        input,
        Trace.empty,
        F.delay(prepare(input.request.reader, input.request.charsetOrUtf8))
      )

  final override def item: RequestItem = items.BodyItem
}

final private[finch] class BinaryBodyStream[F[_], S[_[_], _]](implicit
    LR: LiftReader[S, F],
    protected val F: Sync[F]
) extends ChunkedBody[F, S, Array[Byte]]
    with (Buf => Array[Byte]) {

  def apply(buf: Buf): Array[Byte] = buf.asByteArray

  protected def prepare(r: Reader[Buf], cs: Charset): Output[S[F, Array[Byte]]] =
    Output.payload(LR(r, this))

  override def toString: String = "binaryBodyStream"
}

final private[finch] class StringBodyStream[F[_], S[_[_], _]](implicit
    LR: LiftReader[S, F],
    protected val F: Sync[F]
) extends ChunkedBody[F, S, String]
    with (Buf => String) {

  def apply(buf: Buf): String = buf.asString(StandardCharsets.UTF_8)

  protected def prepare(r: Reader[Buf], cs: Charset): Output[S[F, String]] = cs match {
    case StandardCharsets.UTF_8 => Output.payload(LR(r, this))
    case _                      => Output.payload(LR(r, _.asString(cs)))
  }

  override def toString: String = "stringBodyStream"
}

final private[finch] class BodyStream[F[_], S[_[_], _], A, CT <: String](implicit
    protected val F: Sync[F],
    LR: LiftReader[S, F],
    A: DecodeStream.Aux[S, F, A, CT]
) extends ChunkedBody[F, S, A] {

  protected def prepare(r: Reader[Buf], cs: Charset): Output[S[F, A]] =
    Output.payload(A(LR(r), cs))

  override def toString: String = "bodyStream"
}
