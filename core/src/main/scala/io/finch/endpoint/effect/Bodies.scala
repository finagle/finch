package io.finch.endpoint.effect

import com.twitter.concurrent.AsyncStream
import com.twitter.io.Buf
import io.finch._
import scala.reflect.ClassTag

trait Bodies[F[_]] { self: EffectInstances[F] =>
  /**
    * An evaluating [[Endpoint]] that reads a binary request body, interpreted as a `Array[Byte]`,
    * into an `Option`. The returned [[Endpoint]] only matches non-chunked (non-streamed) requests.
    */
  val binaryBodyOption: Endpoint[F, Option[Array[Byte]]] = io.finch.endpoint.binaryBodyOption[F]

  /**
    * An evaluating [[Endpoint]] that reads a required binary request body, interpreted as an
    * `Array[Byte]`, or throws a [[Error.NotPresent]] exception. The returned [[Endpoint]] only
    * matches non-chunked (non-streamed) requests.
    */
  val binaryBody: Endpoint[F, Array[Byte]] = io.finch.endpoint.binaryBody[F]

  /**
    * An evaluating [[Endpoint]] that reads an optional request body, interpreted as a `String`, into
    * an `Option`. The returned [[Endpoint]] only matches non-chunked (non-streamed) requests.
    */
  val stringBodyOption: Endpoint[F, Option[String]] = io.finch.endpoint.stringBodyOption[F]

  /**
    * An evaluating [[Endpoint]] that reads the required request body, interpreted as a `String`, or
    * throws an [[Error.NotPresent]] exception. The returned [[Endpoint]] only matches non-chunked
    * (non-streamed) requests.
    */
  val stringBody: Endpoint[F, String] = io.finch.endpoint.stringBody[F]

  /**
    * An [[Endpoint]] that reads an optional request body represented as `CT` (`ContentType`) and
    * interpreted as `A`, into an `Option`. The returned [[Endpoint]] only matches non-chunked
    * (non-streamed) requests.
    */
  def bodyOption[A, CT](implicit
                        d: Decode.Dispatchable[A, CT], ct: ClassTag[A]
                       ): Endpoint[F, Option[A]] = io.finch.endpoint.bodyOption[F, A, CT]

  /**
    * An [[Endpoint]] that reads the required request body represented as `CT` (`ContentType`) and
    * interpreted as `A`, or throws an [[Error.NotPresent]] exception. The returned [[Endpoint]]
    * only matches non-chunked (non-streamed) requests.
    */
  def body[A, CT](implicit
                  d: Decode.Dispatchable[A, CT],
                  ct: ClassTag[A]
                 ): Endpoint[F, A] = io.finch.endpoint.body[F, A, CT]

  /**
    * Alias for `body[A, Application.Json]`.
    */
  def jsonBody[A: Decode.Json : ClassTag]: Endpoint[F, A] = io.finch.endpoint.jsonBody[F, A]

  /**
    * Alias for `bodyOption[A, Application.Json]`.
    */
  def jsonBodyOption[A: Decode.Json : ClassTag]: Endpoint[F, Option[A]] =
    io.finch.endpoint.jsonBodyOption[F, A]

  /**
    * Alias for `body[A, Text.Plain]`
    */
  def textBody[A: Decode.Text : ClassTag]: Endpoint[F, A] = io.finch.endpoint.textBody[F, A]

  /**
    * Alias for `bodyOption[A, Text.Plain]`
    */
  def textBodyOption[A: Decode.Text : ClassTag]: Endpoint[F, Option[A]] =
    io.finch.endpoint.textBodyOption[F, A]

  /**
    * An evaluating [[Endpoint]] that reads a required chunked streaming binary body, interpreted as
    * an `AsyncStream[Buf]`. The returned [[Endpoint]] only matches chunked (streamed) requests.
    */
  val asyncBody: Endpoint[F, AsyncStream[Buf]] = io.finch.endpoint.asyncBody[F]
}
