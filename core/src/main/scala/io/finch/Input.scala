package io.finch

import cats.Eq
import cats.effect.Effect
import com.twitter.finagle.http.{Method, Request}
import com.twitter.finagle.netty3.ChannelBufferBuf
import com.twitter.io.{Buf, Reader}
import java.nio.charset.{Charset, StandardCharsets}
import org.jboss.netty.handler.codec.http.{DefaultHttpRequest, HttpMethod, HttpVersion}
import org.jboss.netty.handler.codec.http.multipart.{DefaultHttpDataFactory, HttpPostRequestEncoder}
import scala.collection.mutable.ListBuffer
import shapeless.Witness

/**
 * An input for [[Endpoint]] that glues two individual pieces together:
 *
 * - Finagle's [[Request]] needed for evaluating (e.g., `body`, `param`)
 * - Finch's route (represented as `Seq[String]`) needed for matching (e.g., `path`)
 */
final case class Input(request: Request, route: List[String]) {

  /**
   * Returns the new `Input` wrapping a given `route`.
   */
  def withRoute(route: List[String]): Input = Input(request, route)

  /**
   * Returns the new `Input` wrapping a given payload. This requires the content-type as a first
   * type parameter (won't be inferred).
   *
   * ```
   *  import io.finch._, io.circe._
   *
   *  val text = Input.post("/").withBody[Text.Plain]("Text Body")
   *  val json = Input.post("/").withBody[Application.Json](Map("json" -> "object"))
   *```
   *
   * Also possible to create chunked inputs passing a stream as an argument.
   *
   *```
   *  import io.finch._, io.finch.iteratee._, cats.effect.IO, io.iteratee.Enumerator
   *  import io.finch.circe._, io.circe.generic.auto._
   *
   *  val enumerateText = Enumerator.enumerate[IO, String]("foo", "bar")
   *  val text = Input.post("/").withBody[Text.Plain](enumerateText)
   *
   *  val enumerateJson = Enumerate.enumerate[IO, Map[String, String]](Map("foo" - "bar"))
   *  val json = Input.post("/").withBody[Application.Json](enumerateJson)
   *```
   */
  def withBody[CT <: String]: Input.Body[CT] = new Input.Body[CT](this)

  /**
   * Returns the new `Input` with `headers` amended.
   */
  def withHeaders(headers: (String, String)*): Input = {
    val copied = Input.copyRequest(request)
    headers.foreach { case (k, v) => copied.headerMap.set(k, v) }

    Input(copied, route)
  }

  /**
   * Returns the new `Input` wrapping a given `application/x-www-form-urlencoded` payload.
   *
   * @note In addition to media type, this will also set charset to UTF-8.
   */
  def withForm(params: (String, String)*): Input = {
    // TODO: Figure out way to do that w/o Netty.
    val dataFactory = new DefaultHttpDataFactory(false) // we don't use disk
    val encoder = new HttpPostRequestEncoder(
      dataFactory,
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, request.path),
      false
    )

    params.foreach {
      case (k, v) => encoder.addBodyAttribute(k, v)
    }

    val req = encoder.finalizeRequest()

    val content = if (req.isChunked) {
      Buf(
        Iterator.continually(encoder.nextChunk())
        .takeWhile(c => !c.isLast)
        .map(c => ChannelBufferBuf.Owned(c.getContent))
        .toVector
      )
    } else ChannelBufferBuf.Owned(req.getContent)

    withBody[Application.WwwFormUrlencoded](content, StandardCharsets.UTF_8)
  }
}

/**
 * Creates an input for [[Endpoint]] from [[Request]].
 */
object Input {

  private final def copyRequest(from: Request): Request =
    copyRequestWithReader(from, from.reader)

  private final def copyRequestWithReader(from: Request, reader: Reader[Buf]): Request = {
    val to = Request(from.version, from.method, from.uri, reader)
    to.setChunked(from.isChunked)
    to.content = from.content
    from.headerMap.foreach { case (k, v) => to.headerMap.put(k, v) }

    to
  }

  /**
   * A helper class that captures the `Content-Type` of the payload.
   */
  class Body[CT <: String](i: Input) {
    def apply[A](body: A)(implicit e: Encode.Aux[A, CT], w: Witness.Aux[CT]): Input =
      apply[A](body, StandardCharsets.UTF_8)

    def apply[A](body: A, charset: Charset)(implicit
      e: Encode.Aux[A, CT], W: Witness.Aux[CT]
    ): Input = {
      val content = e(body, charset)
      val copied = copyRequest(i.request)

      copied.setChunked(false)
      copied.content = content
      copied.contentType = W.value
      copied.contentLength = content.length.toLong
      copied.charset = charset.displayName().toLowerCase

      Input(copied, i.route)
    }

    def apply[F[_]: Effect, S[_[_], _], A](s: S[F, A])(implicit
      S: EncodeStream.Aux[F, S, A, CT], W: Witness.Aux[CT]
    ): Input = apply[F, S, A](s, StandardCharsets.UTF_8)

    def apply[F[_], S[_[_], _], A](s: S[F, A], charset: Charset)(implicit
      F: Effect[F],
      S: EncodeStream.Aux[F, S, A, CT],
      W: Witness.Aux[CT]
    ): Input = {
      val content = F.toIO(S(s, charset)).unsafeRunSync()
      val copied = copyRequestWithReader(i.request, content)

      copied.setChunked(true)
      copied.contentType = W.value
      copied.headerMap.setUnsafe("Transfer-Encoding", "chunked")
      copied.charset = charset.displayName().toLowerCase

      Input(copied, i.route)
    }
  }

  implicit val inputEq: Eq[Input] = Eq.fromUniversalEquals

  /**
   * Creates an [[Input]] from a given [[Request]].
   */
  def fromRequest(req: Request): Input = {
    val p = req.path

    if (p.length == 1) Input(req, Nil)
    else {
      val route = new ListBuffer[String]
      var i, j = 1 // drop the first slash

      while (j < p.length) {
        if (p.charAt(j) == '/') {
          route += p.substring(i, j)
          i = j + 1
        }

        j += 1
      }

      if (j > i) {
        route += p.substring(i, j)
      }

      Input(req, route.toList)
    }
  }

  /**
   * Creates a `GET` input with a given query string (represented as `params`).
   */
  def get(path: String, params: (String, String)*): Input =
    fromRequest(Request(Method.Get, Request.queryString(path, params: _*)))

  /**
   * Creates a `PUT` input with a given query string (represented as `params`).
   */
  def put(path: String, params: (String, String)*): Input =
    fromRequest(Request(Method.Put, Request.queryString(path, params: _*)))

  /**
   * Creates a `PATCH` input with a given query string (represented as `params`).
   */
  def patch(path: String, params: (String, String)*): Input =
    fromRequest(Request(Method.Patch, Request.queryString(path, params: _*)))

  /**
   * Creates a `DELETE` input with a given query string (represented as `params`).
   */
  def delete(path: String, params: (String, String)*): Input =
    fromRequest(Request(Method.Delete, Request.queryString(path, params: _*)))

  /**
   * Creates a `POST` input with empty payload.
   */
  def post(path: String): Input = fromRequest(Request(Method.Post, path))
}
