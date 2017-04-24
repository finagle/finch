package io.finch.endpoint

import com.twitter.concurrent.AsyncStream
import com.twitter.io.Buf
import com.twitter.util.{Future, Return, Throw}
import io.catbird.util.Rerunnable
import io.finch._
import io.finch.internal._
import io.finch.items._
import java.nio.charset.Charset
import scala.reflect.ClassTag

private abstract class FullBody[A] extends Endpoint[A] {

  protected def missing: Rerunnable[Output[A]]
  protected def present(content: Buf, cs: Charset): Rerunnable[Output[A]]

  final def apply(input: Input): Endpoint.Result[A] =
    if (input.request.isChunked) EndpointResult.Skipped
    else EndpointResult.Matched(input,
      if (input.request.contentLength.isEmpty) missing
      else present(input.request.content, input.request.charsetOrUtf8))

  final override def item: RequestItem = items.BodyItem
}

private trait PreparedBody[A, B] { _: FullBody[B] =>
  protected def prepare(a: A): B
}

private trait Required[A] extends PreparedBody[A, A] { _: FullBody[A] =>
  protected def prepare(a: A): A = a
  protected def missing: Rerunnable[Output[A]] = Rs.bodyNotPresent[A]
}

private trait Optional[A] extends PreparedBody[A, Option[A]] { _: FullBody[Option[A]] =>
  protected def prepare(a: A): Option[A] = Some(a)
  protected def missing: Rerunnable[Output[Option[A]]] = Rs.none[A]
}

private abstract class Body[A, B, CT <: String](
    d: Decode.Aux[A, CT], ct: ClassTag[A]) extends FullBody[B] with PreparedBody[A, B] {

  protected def present(content: Buf, cs: Charset): Rerunnable[Output[B]] = d(content, cs) match {
    case Return(r) => Rs.payload(prepare(r))
    case Throw(t) => Rerunnable.fromFuture(Future.exception(Error.NotParsed(items.BodyItem, ct, t)))
  }

  final override def toString: String = "body"
}

private abstract class BinaryBody[A] extends FullBody[A] with PreparedBody[Array[Byte], A] {
  protected def present(content: Buf, cs: Charset): Rerunnable[Output[A]] =
    Rs.payload(prepare(content.asByteArray))

  final override def toString: String = "binaryBody"
}

private abstract class StringBody[A] extends FullBody[A] with PreparedBody[String, A] {
  protected def present(content: Buf, cs: Charset): Rerunnable[Output[A]] =
    Rs.payload(prepare(content.asString(cs)))

  final override def toString: String = "stringBody"
}

private[finch] trait Bodies {

  /**
   * An evaluating [[Endpoint]] that reads a binary request body, interpreted as a `Array[Byte]`,
   * into an `Option`. The returned [[Endpoint]] only matches non-chunked (non-streamed) requests.
   */
  val binaryBodyOption: Endpoint[Option[Array[Byte]]] =
    new BinaryBody[Option[Array[Byte]]] with Optional[Array[Byte]]

  /**
   * An evaluating [[Endpoint]] that reads a required binary request body, interpreted as an
   * `Array[Byte]`, or throws a [[Error.NotPresent]] exception. The returned [[Endpoint]] only
   * matches non-chunked (non-streamed) requests.
   */
  val binaryBody: Endpoint[Array[Byte]] =
    new BinaryBody[Array[Byte]] with Required[Array[Byte]]

  /**
   * An evaluating [[Endpoint]] that reads an optional request body, interpreted as a `String`, into
   * an `Option`. The returned [[Endpoint]] only matches non-chunked (non-streamed) requests.
   */
  val stringBodyOption: Endpoint[Option[String]] =
    new StringBody[Option[String]] with Optional[String]

  /**
   * An evaluating [[Endpoint]] that reads the required request body, interpreted as a `String`, or
   * throws an [[Error.NotPresent]] exception. The returned [[Endpoint]] only matches non-chunked
   * (non-streamed) requests.
   */
  val stringBody: Endpoint[String] = new StringBody[String] with Required[String]

  /**
   * An [[Endpoint]] that reads an optional request body represented as `CT` (`ContentType`) and
   * interpreted as `A`, into an `Option`. The returned [[Endpoint]] only matches non-chunked
   * (non-streamed) requests.
   */
  def bodyOption[A, CT <: String](implicit
    d: Decode.Aux[A, CT], ct: ClassTag[A]
  ): Endpoint[Option[A]] = new Body[A, Option[A], CT](d, ct) with Optional[A]

  /**
   * An [[Endpoint]] that reads the required request body represented as `CT` (`ContentType`) and
   * interpreted as `A`, or throws an [[Error.NotPresent]] exception. The returned [[Endpoint]]
   * only matches non-chunked (non-streamed) requests.
   */
  def body[A, CT <: String](implicit d: Decode.Aux[A, CT], ct: ClassTag[A]): Endpoint[A] =
    new Body[A, A, CT](d, ct) with Required[A]

  /**
   * Alias for `body[A, Application.Json]`.
   */
  def jsonBody[A: Decode.Json : ClassTag]: Endpoint[A] = body[A, Application.Json]

  /**
   * Alias for `bodyOption[A, Application.Json]`.
   */
  def jsonBodyOption[A: Decode.Json : ClassTag]: Endpoint[Option[A]] =
    bodyOption[A, Application.Json]

  /**
   * Alias for `body[A, Text.Plain]`
   */
  def textBody[A: Decode.Text : ClassTag]: Endpoint[A] = body[A, Text.Plain]

  /**
   * Alias for `bodyOption[A, Text.Plain]`
   */
  def textBodyOption[A: Decode.Text : ClassTag]: Endpoint[Option[A]] = bodyOption[A, Text.Plain]

  /**
   * An evaluating [[Endpoint]] that reads a required chunked streaming binary body, interpreted as
   * an `AsyncStream[Buf]`. The returned [[Endpoint]] only matches chunked (streamed) requests.
   */
  val asyncBody: Endpoint[AsyncStream[Buf]] = new Endpoint[AsyncStream[Buf]] {
    final def apply(input: Input): Endpoint.Result[AsyncStream[Buf]] =
      if (!input.request.isChunked) EndpointResult.Skipped
      else EndpointResult.Matched(input,
        Rerunnable(Output.payload(AsyncStream.fromReader(input.request.reader))))

    final override def item: RequestItem = items.BodyItem
    final override def toString: String = "asyncBody"
  }
}
