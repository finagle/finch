package io.finch

import cats.Eq
import com.twitter.finagle.http.{MediaType, Method, Request}
import com.twitter.finagle.netty3.ChannelBufferBuf
import com.twitter.io.{Buf, ConcatBuf}
import org.jboss.netty.handler.codec.http.{DefaultHttpRequest, HttpMethod, HttpVersion}
import org.jboss.netty.handler.codec.http.multipart.{DefaultHttpDataFactory, HttpPostRequestEncoder}

/**
 * An input for [[Endpoint]].
 */
final case class Input(request: Request, path: Seq[String]) {
  def headOption: Option[String] = path.headOption
  def drop(n: Int): Input = copy(path = path.drop(n))
  def isEmpty: Boolean = path.isEmpty

  /**
   * Overrides (mutates) the payload (`buf` and `contentType`) of this input and
   * returns `this`.
   *
   * @note The `contentType` value is passed as an HTTP header so it might also
   *       contain a charset separated by `;`.
   */
  def withBody(buf: Buf, contentType: Option[String] = None): Input = {
    request.content = buf

    request.contentLength = buf.length.toLong
    contentType.foreach(ct => request.contentType = ct)

    this
  }

  /**
   * Adds (mutates) new `headers` to this input and returns `this`.
   */
  def withHeaders(headers: (String, String)*): Input = {
    headers.foreach { case (k, v) => request.headerMap.set(k, v) }
    this
  }

  /**
   * Overrides (mutates) the payload of this input to be `application/x-www-form-urlencoded`.
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
      ConcatBuf(
        Iterator.continually(encoder.nextChunk())
        .takeWhile(c => !c.isLast)
        .map(c => ChannelBufferBuf.Owned(c.getContent))
        .toVector
      )
    } else ChannelBufferBuf.Owned(req.getContent)

    withBody(content, Some(MediaType.WwwForm + ";charset=utf-8"))
  }
}

/**
 * Creates an input for [[Endpoint]] from [[Request]].
 */
object Input {

  implicit val inputEq: Eq[Input] = Eq.fromUniversalEquals

  /**
   * Creates an [[Input]] from a given [[Request]].
   */
  def request(req: Request): Input = Input(req, req.path.split("/").toList.drop(1))

  /**
   * Creates a `GET` input with a given query string (represented as `params`).
   */
  def get(path: String, params: (String, String)*): Input =
    request(Request(Method.Get, Request.queryString(path, params: _*)))

  /**
   * Creates a `PUT` input with a given query string (represented as `params`).
   */
  def put(path: String, params: (String, String)*): Input =
    request(Request(Method.Put, Request.queryString(path, params: _*)))

  /**
   * Creates a `PATCH` input with a given query string (represented as `params`).
   */
  def patch(path: String, params: (String, String)*): Input =
    request(Request(Method.Patch, Request.queryString(path, params: _*)))

  /**
   * Creates a `DELETE` input with a given query string (represented as `params`).
   */
  def delete(path: String, params: (String, String)*): Input =
    request(Request(Method.Delete, Request.queryString(path, params: _*)))

  /**
   * Creates a `POST` input with empty payload.
   */
  def post(path: String): Input = request(Request(Method.Post, path))
}
