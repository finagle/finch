package io.finch

import cats.{Applicative, Eq}
import com.twitter.finagle.http.{Cookie, Response, Status}
import java.nio.charset.{Charset, StandardCharsets}

/**
 * An output of [[Endpoint]].
 */
sealed trait Output[+A] { self =>

  /**
   * The status code of this [[Output]].
   */
  def status: Status

  /**
   * The header map of this [[Output]].
   */
  def headers: Map[String, String]

  /**
   * The cookie list of this [[Output]].
   */
  def cookies: List[Cookie]

  /**
   * The charset of this [[Output]].
   */
  def charset: Option[Charset]

  /**
   * Returns the payload value of this [[Output]] or throws an exception.
   */
  def value: A

  final def map[B](fn: A => B): Output[B] = this match {
    case p: Output.Payload[A] => p.withValue(fn(p.value))
    case f: Output.Failure => f
    case e: Output.Empty => e
  }

  final def flatMap[B](fn: A => Output[B]): Output[B] = this match {
    case p: Output.Payload[A] => fn(p.value).withCookies(p.cookies).withHeaders(p.headers)
    case f: Output.Failure => f
    case e: Output.Empty => e
  }

  final def traverse[F[_], B](fn: A => F[B])(implicit F: Applicative[F]): F[Output[B]] = this match {
    case p: Output.Payload[A] => F.map(fn(p.value))(b => p.withValue(b))
    case f: Output.Failure => F.pure(f)
    case e: Output.Empty => F.pure(e)
  }

  final def traverseFlatten[F[_], B](fn: A => F[Output[B]])(implicit F: Applicative[F]): F[Output[B]] = this match {
    case p: Output.Payload[A] =>
      F.map(fn(p.value))(ob => ob.withHeaders(self.headers).withCookies(self.cookies))
    case f: Output.Failure => F.pure(f)
    case e: Output.Empty => F.pure(e)
  }

  /**
   * Overrides `charset` of this [[Output]].
   */
  final def withCharset(charset: Charset): Output[A] =
    copy(charset = Some(charset))

  /**
   * Overrides the `status` code of this [[Output]].
   */
  final def withStatus(status: Status): Output[A] =
    copy(status = status)

  /**
   * Adds given `headers` to this [[Output]].
   */
  final def withHeaders(headers: Map[String, String]): Output[A] =
    if (headers.isEmpty) this
    else copy(headers = self.headers ++ headers)

  /**
   * Adds given `cookies` to this [[Output]].
   */
  final def withCookies(cookies: List[Cookie]): Output[A] =
    if (cookies.isEmpty) this
    else copy(cookies = self.cookies ++ cookies)

  /**
   * Adds a given `header` to this [[Output]].
   */
  final def withHeader(header: (String, String)): Output[A] = withHeaders(Map(header))

  /**
   * Adds a given `cookie` to this [[Output]].
   */
  final def withCookie(cookie: Cookie): Output[A] = withCookies(List(cookie))

  protected def copy(status: Status = self.status,
    charset: Option[Charset] = self.charset,
    headers: Map[String, String] = self.headers,
    cookies: List[Cookie] = self.cookies): Output[A]
}

object Output {

  /**
   * Creates a successful [[Output]] that wraps a payload `value` with given `status`.
   */
  final def payload[A](value: A, status: Status = Status.Ok): Output[A] =
    Payload(value, status)

  /**
   * Creates a failure [[Output]] that wraps an exception `cause` causing this.
   */
  final def failure[A](cause: Exception, status: Status = Status.BadRequest): Output[A] =
    Failure(cause, status)

  /**
   * Creates an empty [[Output]] of given `status`.
   */
  final def empty[A](status: Status): Output[A] = Empty(status)

  /**
   * Creates a unit/empty [[Output]] (i.e., `Output[Unit]`) of given `status`.
   */
  final def unit(status: Status): Output[Unit] = empty(status)

  /**
   * An [[Output]] with `None` as a payload.
   */
  val None: Output[Option[Nothing]] = Output.payload(Option.empty[Nothing])

  /**
   * An [[Output]] with [[shapeless.HNil]] as a payload.
   */
  val HNil: Output[shapeless.HNil] = Output.payload(shapeless.HNil)

  /**
   * A successful [[Output]] that captures a payload `value`.
   */
  private[finch] final case class Payload[A](
      value: A,
      status: Status = Status.Ok,
      charset: Option[Charset] = Option.empty,
      headers: Map[String, String] = Map.empty[String, String],
      cookies: List[Cookie] = List.empty[Cookie]) extends  Output[A] { self =>

    def withValue[B](value: B): Payload[B] = Payload(value, status, charset, headers, cookies)

    protected def copy(
        status: Status,
        charset: Option[Charset],
        headers: Map[String, String],
        cookies: List[Cookie]): Output[A] = Payload(value, status, charset, headers, cookies)
  }

  /**
   * A failure [[Output]] that captures an  [[Exception]] explaining why it's not a payload
   * or an empty response.
   */
  private[finch] final case class Failure(
      cause: Exception,
      status: Status = Status.BadRequest,
      charset: Option[Charset] = Option.empty,
      headers: Map[String, String] = Map.empty[String, String],
      cookies: List[Cookie] = List.empty[Cookie]) extends Output[Nothing] {

    def value: Nothing = throw cause

    protected def copy(
        status: Status,
        charset: Option[Charset],
        headers: Map[String, String],
        cookies: List[Cookie]): Output[Nothing] = Failure(cause, status, charset, headers, cookies)
  }

  /**
   * An empty [[Output]] that does not capture any payload.
   */
  private[finch] final case class Empty(
      status: Status,
      charset: Option[Charset] = Option.empty,
      headers: Map[String, String] = Map.empty[String, String],
      cookies: List[Cookie] = List.empty[Cookie]) extends Output[Nothing] {

    def value: Nothing = throw new IllegalStateException("empty output")

    protected def copy(
        status: Status,
        charset: Option[Charset],
        headers: Map[String, String],
        cookies: List[Cookie]): Output[Nothing] = Empty(status, charset, headers, cookies)
  }

  implicit def outputEq[A]: Eq[Output[A]] = Eq.fromUniversalEquals

  implicit class OutputOps[A](val o: Output[A]) extends AnyVal {

    /**
     * Converts this [[Output]] to the HTTP response of the given `version`.
     */
    def toResponse[F[_], CT](implicit
      F: Applicative[F],
      tr: ToResponse.Aux[F, A, CT],
      tre: ToResponse.Aux[F, Exception, CT]
    ): F[Response] = {
      val rep0 = o match {
        case p: Output.Payload[A] => tr(p.value, p.charset.getOrElse(StandardCharsets.UTF_8))
        case f: Output.Failure => tre(f.cause, f.charset.getOrElse(StandardCharsets.UTF_8))
        case _: Output.Empty => F.pure(Response())
      }

      F.map(rep0) { rep =>
        rep.status = o.status

        o.headers.foreach { case (k, v) => rep.headerMap.set(k, v) }
        o.cookies.foreach(rep.cookies.add)
        o.charset.foreach { c =>
          if (!rep.content.isEmpty || rep.isChunked) {
            rep.charset = c.displayName.toLowerCase
          }
        }

        rep
      }
    }
  }
}
