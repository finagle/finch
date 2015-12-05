package io.finch

import com.twitter.finagle.http.{Cookie, Response, Status, Version}
import com.twitter.util.{Await, Future}
import io.finch.internal.ToResponse

/**
 * An output of [[Endpoint]].
 */
sealed trait Output[+A] { self =>
  def value: A
  def status: Status
  def headers: Map[String, String]
  def cookies: Seq[Cookie]
  def contentType: Option[String]
  def charset: Option[String]

  def map[B](fn: A => B): Output[B]
  def flatMap[B](fn: A => Output[B]): Output[B]
  def flatten[B](implicit ev: A <:< Output[B]): Output[B]
  def traverse[B](fn: A => Future[B]): Future[Output[B]]

  protected def copy0(
    headers: Map[String, String] = self.headers,
    cookies: Seq[Cookie] = self.cookies,
    contentType: Option[String] = self.contentType,
    charset: Option[String] = self.charset
  ): Output[A]

  def withHeader(header: (String, String)): Output[A] = copy0(headers = headers + header)
  def withCookie(cookie: Cookie): Output[A] = copy0(cookies = cookies :+ cookie)
  def withContentType(contentType: Option[String]): Output[A] = copy0(contentType = contentType)
  def withCharset(charset: Option[String]): Output[A] = copy0(charset = charset)

  def toResponse(version: Version = Version.Http11)(implicit
    payloadToResponse: ToResponse[A],
    failureToResponse: ToResponse[Exception]
  ): Response = {
    val rep = this match {
      case Output.Payload(v, _, _, _, _, _) => payloadToResponse(v)
      case Output.Failure(x, _, _, _, _, _) => failureToResponse(x)
    }
    rep.version = version
    rep.status = status
    headers.foreach { case (k, v) => rep.headerMap.set(k, v) }
    cookies.foreach(rep.cookies.add)
    val cs = rep.charset
    contentType.foreach { ct => rep.contentType = ct }
    charset.orElse(cs).foreach { chr => rep.charset = chr }

    rep
  }
}

object Output {

  /**
   * Creates a new [[Output.Payload]] of type `Output[A]`.
   */
  def payload[A](a: A, s: Status = Status.Ok): Output[A] = Payload(a, s)

  /**
   * Creates a new [[Output.Failure]] of type `Output[A]`.
   */
  def failure[A](cause: Exception, s: Status = Status.BadRequest): Output[A] = Failure(cause, s)

  /**
   * A successful [[Output]] that captures a payload `value`.
   */
  final case class Payload[A](
    value: A,
    status: Status = Status.Ok,
    headers: Map[String, String] = Map.empty[String, String],
    cookies: Seq[Cookie] = Seq.empty[Cookie],
    contentType: Option[String] = None,
    charset: Option[String] = None
  ) extends Output[A] {

    def map[B](fn: A => B): Output[B] = copy(value = fn(value))
    def flatMap[B](fn: A => Output[B]): Output[B] = {
      val ob = fn(value)
      ob.copy0(
        headers = ob.headers ++ headers,
        cookies = ob.cookies ++ cookies,
        contentType = ob.contentType.orElse(contentType),
        charset = ob.charset.orElse(charset)
      )
    }
    def flatten[B](implicit ev: <:<[A, Output[B]]): Output[B] = value
    def traverse[B](fn: A => Future[B]): Future[Output[B]] = fn(value).map(b => copy(value = b))

    protected def copy0(
      headers: Map[String, String],
      cookies: Seq[Cookie],
      contentType: Option[String],
      charset: Option[String]
    ): Output[A] = copy(
      headers = headers, cookies = cookies, contentType = contentType, charset = charset
    )
  }

  /**
   * A failure [[Output]] that captures an [[Exception]] that caused this.
   */
  final case class Failure(
    cause: Exception,
    status: Status = Status.BadRequest,
    headers: Map[String, String] = Map.empty[String, String],
    cookies: Seq[Cookie] = Seq.empty[Cookie],
    contentType: Option[String] = None,
    charset: Option[String] = None
  ) extends Output[Nothing] {

    def value: Nothing = throw cause

    def map[B](fn: Nothing => B): Output[B] = this
    def flatMap[B](fn: Nothing => Output[B]): Output[B] = this
    def flatten[B](implicit ev: <:<[Nothing, Output[B]]): Output[B] = this
    def traverse[B](fn: Nothing => Future[B]): Future[Output[B]] = Future.value(this)

    protected def copy0(
      headers: Map[String, String],
      cookies: Seq[Cookie],
      contentType: Option[String],
      charset: Option[String]
    ): Output[Nothing] = copy(
      headers = headers, cookies = cookies, contentType = contentType, charset = charset
    )
  }

  implicit class EndpointResultOps[A](val o: Endpoint.Result[A]) extends AnyVal {
    private[finch] def output: Option[Output[A]] = o.map({ case (_, oa) => Await.result(oa.value) })
    private[finch] def value: Option[A] = output.map(oa => oa.value)
    private[finch] def remainder: Option[Input] = o.map(_._1)
  }
}
