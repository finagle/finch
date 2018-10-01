package io.finch.endpoint

import cats.effect.Effect
import com.twitter.io.Buf
import com.twitter.util.{Return, Throw, Try}
import io.finch._
import io.finch.internal._
import io.finch.items._
import java.nio.charset.Charset
import scala.reflect.ClassTag

private[finch] abstract class FullBody[F[_], A] extends Endpoint[F, A] {

  protected def F: Effect[F]
  protected def missing: F[Output[A]]
  protected def present(contentType: String, content: Buf, cs: Charset): F[Output[A]]

  final def apply(input: Input): EndpointResult[F, A] =
    if (input.request.isChunked) EndpointResult.NotMatched
    else {
      val output = F.suspend {
        val contentLength = input.request.contentLengthOrNull
        if (contentLength == null || contentLength == "0") missing
        else present(
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

private[finch] abstract class Body[F[_], A, B, CT](implicit
  dd: Decode.Dispatchable[A, CT],
  ct: ClassTag[A],
  protected val F: Effect[F]
) extends FullBody[F, B] with FullBody.PreparedBody[F, A, B] with (Try[A] => Try[Output[B]]) {

  final def apply(ta: Try[A]): Try[Output[B]] = ta match {
    case Return(r) => Return(Output.payload(prepare(r)))
    case Throw(t) => Throw(Error.NotParsed(items.BodyItem, ct, t))
  }

  protected def present(contentType: String, content: Buf, cs: Charset): F[Output[B]] =
    dd(contentType, content, cs).transform(this) match {
      case Return(r) => F.pure(r)
      case Throw(t) => F.raiseError(t)
    }

  final override def toString: String = "body"
}

private[finch] abstract class BinaryBody[F[_], A](implicit protected val F: Effect[F])
  extends FullBody[F, A] with FullBody.PreparedBody[F, Array[Byte], A] {

  protected def present(contentType: String, content: Buf, cs: Charset): F[Output[A]] =
    F.pure(Output.payload(prepare(content.asByteArray)))

  final override def toString: String = "binaryBody"
}

private[finch] abstract class StringBody[F[_], A](implicit protected val F: Effect[F])
  extends FullBody[F, A]
  with FullBody.PreparedBody[F, String, A] {

  protected def present(contentType: String, content: Buf, cs: Charset): F[Output[A]] =
    F.pure(Output.payload(prepare(content.asString(cs))))

  final override def toString: String = "stringBody"
}
