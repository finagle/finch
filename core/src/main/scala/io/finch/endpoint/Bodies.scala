package io.finch.endpoint

import cats.effect.Effect
import com.twitter.concurrent.AsyncStream
import com.twitter.io.Buf
import com.twitter.util.{Return, Throw, Try}
import io.finch._
import io.finch.internal._
import io.finch.items._
import java.nio.charset.Charset
import scala.reflect.ClassTag


private[finch] class Bodies[F[_] : Effect] {

  abstract class FullBody[A] extends Endpoint[F, A] {

    protected def missing: F[Output[A]]
    protected def present(contentType: String, content: Buf, cs: Charset): F[Output[A]]

    final def apply(input: Input): EndpointResult[F, A] =
      if (input.request.isChunked) EndpointResult.NotMatched
      else {
        val output = Effect[F].suspend {
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

  object FullBody {

    private val notPresentInstance: F[Output[Nothing]] =
      Effect[F].raiseError(Error.NotPresent(items.BodyItem))

    private val noneInstance: F[Output[Option[Nothing]]] = Effect[F].pure(Output.None)

    private def notPresent[A] = notPresentInstance.asInstanceOf[F[Output[A]]]
    private def none[A] = noneInstance.asInstanceOf[F[Output[Option[A]]]]

    trait PreparedBody[A, B] { _: FullBody[B] =>
      protected def prepare(a: A): B
    }

    trait Required[A] extends PreparedBody[A, A] { _: FullBody[A] =>
      protected def prepare(a: A): A = a
      protected def missing: F[Output[A]] = notPresent[A]
    }

    trait Optional[A] extends PreparedBody[A, Option[A]] { _: FullBody[Option[A]] =>
      protected def prepare(a: A): Option[A] = Some(a)
      protected def missing: F[Output[Option[A]]] = none[A]
    }
  }

  abstract class Body[A, B, CT](ad: Decode.Dispatchable[A, CT], ct: ClassTag[A])
    extends FullBody[B] with FullBody.PreparedBody[A, B] with (Try[A] => Try[Output[B]]) {

    final def apply(ta: Try[A]): Try[Output[B]] = ta match {
      case Return(r) => Return(Output.payload(prepare(r)))
      case Throw(t) => Throw(Error.NotParsed(items.BodyItem, ct, t))
    }

    protected def present(contentType: String, content: Buf, cs: Charset): F[Output[B]] =
      ad(contentType, content, cs).transform(this) match {
        case Return(r) => Effect[F].pure(r)
        case Throw(t) => Effect[F].raiseError(t)
      }

    final override def toString: String = "body"
  }

  abstract class BinaryBody[A]
    extends FullBody[A] with FullBody.PreparedBody[Array[Byte], A] {

    protected def present(contentType: String, content: Buf, cs: Charset): F[Output[A]] =
      Effect[F].pure(Output.payload(prepare(content.asByteArray)))

    final override def toString: String = "binaryBody"
  }

  abstract class StringBody[A] extends FullBody[A] with FullBody.PreparedBody[String, A] {
    protected def present(contentType: String, content: Buf, cs: Charset): F[Output[A]] =
      Effect[F].pure(Output.payload(prepare(content.asString(cs))))

    final override def toString: String = "stringBody"
  }

}

private[finch] trait BodyEndpoints {

  /**
    * An evaluating [[Endpoint]] that reads a binary request body, interpreted as a `Array[Byte]`,
    * into an `Option`. The returned [[Endpoint]] only matches non-chunked (non-streamed) requests.
    */
  def binaryBodyOption[F[_] : Effect]: Endpoint[F, Option[Array[Byte]]] = {
    val bodies = new Bodies[F]
    new bodies.BinaryBody[Option[Array[Byte]]] with bodies.FullBody.Optional[Array[Byte]]
  }

  /**
    * An evaluating [[Endpoint]] that reads a required binary request body, interpreted as an
    * `Array[Byte]`, or throws a [[Error.NotPresent]] exception. The returned [[Endpoint]] only
    * matches non-chunked (non-streamed) requests.
    */
  def binaryBody[F[_] : Effect]: Endpoint[F, Array[Byte]] = {
    val bodies = new Bodies[F]
    new bodies.BinaryBody[Array[Byte]] with bodies.FullBody.Required[Array[Byte]]
  }

  /**
    * An evaluating [[Endpoint]] that reads an optional request body, interpreted as a `String`, into
    * an `Option`. The returned [[Endpoint]] only matches non-chunked (non-streamed) requests.
    */
  def stringBodyOption[F[_] : Effect]: Endpoint[F, Option[String]] = {
    val bodies = new Bodies[F]
    new bodies.StringBody[Option[String]] with bodies.FullBody.Optional[String]
  }

  /**
    * An evaluating [[Endpoint]] that reads the required request body, interpreted as a `String`, or
    * throws an [[Error.NotPresent]] exception. The returned [[Endpoint]] only matches non-chunked
    * (non-streamed) requests.
    */
  def stringBody[F[_] : Effect]: Endpoint[F, String] = {
    val bodies = new Bodies[F]
    new bodies.StringBody[String] with bodies.FullBody.Required[String]
  }

  /**
    * An [[Endpoint]] that reads an optional request body represented as `CT` (`ContentType`) and
    * interpreted as `A`, into an `Option`. The returned [[Endpoint]] only matches non-chunked
    * (non-streamed) requests.
    */
  def bodyOption[F[_] : Effect, A, CT](implicit
    d: Decode.Dispatchable[A, CT], ct: ClassTag[A]
  ): Endpoint[F, Option[A]] = {
    val bodies = new Bodies[F]
    new bodies.Body[A, Option[A], CT](d, ct) with bodies.FullBody.Optional[A]
  }

  /**
    * An [[Endpoint]] that reads the required request body represented as `CT` (`ContentType`) and
    * interpreted as `A`, or throws an [[Error.NotPresent]] exception. The returned [[Endpoint]]
    * only matches non-chunked (non-streamed) requests.
    */
  def body[F[_] : Effect, A, CT](implicit
    d: Decode.Dispatchable[A, CT],
    ct: ClassTag[A]
  ): Endpoint[F, A] = {
    val bodies = new Bodies[F]
    new bodies.Body[A, A, CT](d, ct) with bodies.FullBody.Required[A]
  }

  /**
    * Alias for `body[A, Application.Json]`.
    */
  def jsonBody[F[_] : Effect, A: Decode.Json : ClassTag]: Endpoint[F, A] =
    body[F, A, Application.Json]

  /**
    * Alias for `bodyOption[A, Application.Json]`.
    */
  def jsonBodyOption[F[_] : Effect,A: Decode.Json : ClassTag]: Endpoint[F, Option[A]] =
    bodyOption[F, A, Application.Json]

  /**
    * Alias for `body[A, Text.Plain]`
    */
  def textBody[F[_] : Effect, A: Decode.Text : ClassTag]: Endpoint[F, A] =
    body[F, A, Text.Plain]

  /**
    * Alias for `bodyOption[A, Text.Plain]`
    */
  def textBodyOption[F[_] : Effect, A: Decode.Text : ClassTag]: Endpoint[F, Option[A]] =
    bodyOption[F, A, Text.Plain]

  /**
    * An evaluating [[Endpoint]] that reads a required chunked streaming binary body, interpreted as
    * an `AsyncStream[Buf]`. The returned [[Endpoint]] only matches chunked (streamed) requests.
    */
  def asyncBody[F[_] : Effect]: Endpoint[F, AsyncStream[Buf]] = new Endpoint[F, AsyncStream[Buf]] {
    final def apply(input: Input): EndpointResult[F, AsyncStream[Buf]] =
      if (!input.request.isChunked) EndpointResult.NotMatched
      else
        EndpointResult.Matched(
          input,
          Trace.empty,
          Effect[F].delay(Output.payload(AsyncStream.fromReader(input.request.reader)))
        )

    final override def item: RequestItem = items.BodyItem
    final override def toString: String = "asyncBody"
  }

}
