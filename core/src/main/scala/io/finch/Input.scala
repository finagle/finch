package io.finch

import java.nio.charset.Charset

import cats.Eq
import com.twitter.finagle.http.{Method, Request, RequestBuilder}
import com.twitter.io.Buf

/**
 * An input for [[Endpoint]].
 */
final case class Input(request: Request, path: Seq[String]) {
  def headOption: Option[String] = path.headOption
  def drop(n: Int): Input = copy(path = path.drop(n))
  def isEmpty: Boolean = path.isEmpty

  def withBody(buf: Buf, charset: Option[Charset] = None): Input = {
    request.content = buf
    request.contentLength = buf.length.toLong
    charset.foreach(cs => request.charset = charset.get.displayName())
    this
  }

  def withHeaders(headers: (String, String)*): Input = {
    headers.foreach { case (k, v) => request.headerMap.set(k, v) }
    this
  }

  def withForm(params: (String, String)*): Input = {
    require(request.host.isDefined, "The host has to be defined")
    require(params.nonEmpty, "Cannot create request without params")
    val req = RequestBuilder()
      .addFormElement(params: _*)
      .url(request.host.get)
      .buildFormPost()
    request.method = req.method
    request.content = req.content
    req.contentLength.foreach(cl => request.contentLength = req.contentLength.get)
    req.charset.foreach(cs => request.charset = req.charset.get)
    this
  }
}

/**
 * Creates an input for [[Endpoint]] from [[Request]].
 */
object Input {
  def apply(req: Request): Input = Input(req, req.path.split("/").toList.drop(1))

  implicit val inputEq: Eq[Input] = Eq.fromUniversalEquals

  def get(path: String, params: (String, String)*): Input = Input(Request(path, params: _*))
  def put(path: String, params: (String, String)*): Input = {
    val req = Request(path, params: _*)
    req.method = Method.Put
    Input(req)
  }
  def patch(path: String, params: (String, String)*): Input = {
    val req = Request(path, params: _*)
    req.method = Method.Patch
    Input(req)
  }
  def delete(path: String, params: (String, String)*): Input = {
    val req = Request(path, params: _*)
    req.method = Method.Patch
    Input(req)
  }
  def post(path: String): Input = Input(Request(Method.Post, path))
}
