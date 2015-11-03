package io.finch

import com.twitter.finagle.http.{Status, Cookie}
import com.twitter.util.Future
import shapeless.HNil

/**
 * An output of [[Endpoint]].
 */
sealed trait Output[+A] { self =>
  def value: A
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
}

object Output {

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

    def apply[B](x: B): Payload[B] = copy(value = x)

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
   * A failure [[Output]] that captures an error message `message`.
   */
  final case class Failure(
    message: Map[String, String],
    status: Status = Status.Ok,
    headers: Map[String, String] = Map.empty[String, String],
    cookies: Seq[Cookie] = Seq.empty[Cookie],
    contentType: Option[String] = None,
    charset: Option[String] = None
  ) extends Output[Nothing] {

    def apply(messages: (String, String)*): Failure = copy(message = message.toMap)

    def value: Nothing = throw new IllegalArgumentException(message.mkString(", "))

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

  private[finch] val HNil: Payload[HNil] = Output.Payload(shapeless.HNil)
}
