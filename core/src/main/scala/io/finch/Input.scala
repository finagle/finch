package io.finch

import cats.Eq
import com.twitter.finagle.http.{Method, Request}
import com.twitter.finagle.netty3.ChannelBufferBuf
import com.twitter.io.Buf
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
final case class Input(request: Request, route: Seq[String]) {

  /**
    * Returns the new `Input` wrapping a given `route`.
    */
  def withRoute(route: Seq[String]): Input = Input(request, route)

  /**
    * Returns the new `Input` wrapping a given payload. This requires the content-type as a first
    * type parameter (won't be inferred).
    *
    * ```
    *  import io.finch._, io.circe._
    *
    *  val text: Input = Input.post("/").withBody[Text.Plain]("Text Body")
    *  val json: Input = Input.post("/").withBody[Application.Json](Map("json" -> "object"))
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
        Iterator
          .continually(encoder.nextChunk())
          .takeWhile(c => !c.isLast)
          .map(c => ChannelBufferBuf.Owned(c.getContent))
          .toVector
      )
    } else ChannelBufferBuf.Owned(req.getContent)

    withBody[Application.WwwFormUrlencoded](content, Some(StandardCharsets.UTF_8))
  }
}

/**
  * Creates an input for [[Endpoint]] from [[Request]].
  */
object Input {

  final private def copyRequest(from: Request): Request = {
    val to = Request()
    to.version = from.version
    to.method = from.method
    to.content = from.content
    to.uri = from.uri
    from.headerMap.foreach { case (k, v) => to.headerMap.put(k, v) }

    to
  }

  /**
    * A helper class that captures the `Content-Type` of the payload.
    */
  class Body[CT <: String](i: Input) {

    def apply[A](
        body: A,
        charset: Option[Charset] = None
      )(implicit
        e: Encode.Aux[A, CT],
        w: Witness.Aux[CT]
      ): Input = {
      val content = e(body, charset.getOrElse(StandardCharsets.UTF_8))

      val copied = copyRequest(i.request)

      copied.content = content
      copied.contentType = w.value
      copied.contentLength = content.length.toLong
      charset.foreach(cs => copied.charset = cs.displayName().toLowerCase)

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
