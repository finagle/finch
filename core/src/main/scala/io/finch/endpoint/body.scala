package io.finch.endpoint

import com.twitter.concurrent.AsyncStream
import com.twitter.io.Buf
import com.twitter.util.{Future, Return, Throw, Try}
import io.catbird.util.Rerunnable
import io.finch._
import io.finch.internal._
import io.finch.items._
import java.nio.charset.Charset
import scala.reflect.ClassTag

private abstract class FullBody[A] extends Endpoint[A] {

  protected def missing: Future[Output[A]]
  protected def present(content: Buf, cs: Charset): Future[Output[A]]

  final def apply(input: Input): Endpoint.Result[A] =
    if (input.request.isChunked) EndpointResult.NotMatched
    else {
      val output = Rerunnable.fromFuture {
        val contentLength = input.request.contentLengthOrNull
        if (contentLength == null || contentLength == "0") missing
        else present(input.request.content, input.request.charsetOrUtf8)
      }

      EndpointResult.Matched(input, output)
    }

  final override def item: RequestItem = items.BodyItem
}

private object FullBody {

  private val notPresentInstance: Future[Output[Nothing]] =
    Future.exception(Error.NotPresent(items.BodyItem))

  private val noneInstance: Future[Output[Option[Nothing]]] = Future.value(Output.None)

  private def notPresent[A] = notPresentInstance.asInstanceOf[Future[Output[A]]]
  private def none[A] = noneInstance.asInstanceOf[Future[Output[Option[A]]]]

  trait PreparedBody[A, B] { _: FullBody[B] =>
    protected def prepare(a: A): B
  }

  trait Required[A] extends PreparedBody[A, A] { _: FullBody[A] =>
    protected def prepare(a: A): A = a
    protected def missing: Future[Output[A]] = notPresent[A]
  }

  trait Optional[A] extends PreparedBody[A, Option[A]] { _: FullBody[Option[A]] =>
    protected def prepare(a: A): Option[A] = Some(a)
    protected def missing: Future[Output[Option[A]]] = none[A]
  }
}

private abstract class Body[A, B, CT <: String](
  d: Decode.Aux[A, CT],
  ct: ClassTag[A]
) extends FullBody[B] with FullBody.PreparedBody[A, B] with (Try[A] => Try[Output[B]]) {

  final def apply(ta: Try[A]): Try[Output[B]] = ta match {
    case Return(r) => Return(Output.payload(prepare(r)))
    case Throw(t) => Throw(Error.NotParsed(items.BodyItem, ct, t))
  }

  protected def present(content: Buf, cs: Charset): Future[Output[B]] =
    Future.const(d(content, cs).transform(this))

  final override def toString: String = "body"
}

private abstract class BinaryBody[A]
    extends FullBody[A] with FullBody.PreparedBody[Array[Byte], A] {

  protected def present(content: Buf, cs: Charset): Future[Output[A]] =
    Future.value(Output.payload(prepare(content.asByteArray)))

  final override def toString: String = "binaryBody"
}

private abstract class StringBody[A] extends FullBody[A] with FullBody.PreparedBody[String, A] {
  protected def present(content: Buf, cs: Charset): Future[Output[A]] =
    Future.value(Output.payload(prepare(content.asString(cs))))

  final override def toString: String = "stringBody"
}

private[finch] trait Bodies {

  /**
   * An evaluating [[Endpoint]] that reads a binary request body, interpreted as a `Array[Byte]`,
   * into an `Option`. The returned [[Endpoint]] only matches non-chunked (non-streamed) requests.
   */
  val binaryBodyOption: Endpoint[Option[Array[Byte]]] =
    new BinaryBody[Option[Array[Byte]]] with FullBody.Optional[Array[Byte]]

  /**
   * An evaluating [[Endpoint]] that reads a required binary request body, interpreted as an
   * `Array[Byte]`, or throws a [[Error.NotPresent]] exception. The returned [[Endpoint]] only
   * matches non-chunked (non-streamed) requests.
   */
  val binaryBody: Endpoint[Array[Byte]] =
    new BinaryBody[Array[Byte]] with FullBody.Required[Array[Byte]]

  /**
   * An evaluating [[Endpoint]] that reads an optional request body, interpreted as a `String`, into
   * an `Option`. The returned [[Endpoint]] only matches non-chunked (non-streamed) requests.
   */
  val stringBodyOption: Endpoint[Option[String]] =
    new StringBody[Option[String]] with FullBody.Optional[String]

  /**
   * An evaluating [[Endpoint]] that reads the required request body, interpreted as a `String`, or
   * throws an [[Error.NotPresent]] exception. The returned [[Endpoint]] only matches non-chunked
   * (non-streamed) requests.
   */
  val stringBody: Endpoint[String] =
    new StringBody[String] with FullBody.Required[String]

  /**
   * An [[Endpoint]] that reads an optional request body represented as `CT` (`ContentType`) and
   * interpreted as `A`, into an `Option`. The returned [[Endpoint]] only matches non-chunked
   * (non-streamed) requests.
   */
  def bodyOption[A, CT <: String](implicit
    d: Decode.Aux[A, CT], ct: ClassTag[A]
  ): Endpoint[Option[A]] = new Body[A, Option[A], CT](d, ct) with FullBody.Optional[A]

  /**
   * An [[Endpoint]] that reads the required request body represented as `CT` (`ContentType`) and
   * interpreted as `A`, or throws an [[Error.NotPresent]] exception. The returned [[Endpoint]]
   * only matches non-chunked (non-streamed) requests.
   */
  def body[A, CT <: String](implicit d: Decode.Aux[A, CT], ct: ClassTag[A]): Endpoint[A] =
    new Body[A, A, CT](d, ct) with FullBody.Required[A]

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
      if (!input.request.isChunked) EndpointResult.NotMatched
      else EndpointResult.Matched(input,
        Rerunnable(Output.payload(AsyncStream.fromReader(input.request.reader))))

    final override def item: RequestItem = items.BodyItem
    final override def toString: String = "asyncBody"
  }
}
