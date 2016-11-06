package io.finch

import cats.Eq
import com.twitter.finagle.http.{Cookie, Response, Status, Version}
import com.twitter.util.{Await, Duration, Future, Try}
import io.finch.internal.ToResponse
import java.nio.charset.{Charset, StandardCharsets}

/**
 * An output of [[Endpoint]].
 */
sealed trait Output[+A] { self =>

  protected def meta: Output.Meta
  protected def withMeta(meta: Output.Meta): Output[A]

  /**
   * The status code of this [[Output]].
   */
  def status: Status = meta.status

  /**
   * The header map of this [[Output]].
   */
  def headers: Map[String, String] = meta.headers

  /**
   * The cookie list of this [[Output]].
   */
  def cookies: Seq[Cookie] = meta.cookies

  /**
   * The charset of this [[Output]].
   */
  def charset: Option[Charset] = meta.charset

  /**
   * Returns the payload value of this [[Output]] or throws an exception.
   */
  def value: A

  def map[B](fn: A => B): Output[B] = this match {
    case Output.Payload(v, m) => Output.Payload(fn(v), m)
    case f @ Output.Failure(_, _) => f
    case e @ Output.Empty(_) => e
  }

  def flatMap[B](fn: A => Output[B]): Output[B] = this match {
    case p @ Output.Payload(v, _) =>
      val ob = fn(v)
      ob.withMeta(ob.meta.copy(
        headers = ob.headers ++ p.headers,
        cookies = ob.cookies ++ p.cookies
      ))

    case f @ Output.Failure(_, _) => f
    case e @ Output.Empty(_) => e
  }

  def flatten[B](implicit ev: A <:< Output[B]): Output[B] = this match {
    case Output.Payload(v, _) => v
    case f @ Output.Failure(_, _) => f
    case e @ Output.Empty(_) => e
  }

  def traverse[B](fn: A => Future[B]): Future[Output[B]] = this match {
    case p @ Output.Payload(v, _) => fn(v).map(b => p.copy(value = b))
    case f @ Output.Failure(_, _) => Future.value(f)
    case e @ Output.Empty(_) => Future.value(e)
  }

  /**
   * Overrides `charset` of this [[Output]].
   */
  def withCharset(charset: Charset): Output[A] =
    withMeta(meta.copy(charset = Some(charset)))

  /**
   * Overrides the `status` code of this [[Output]].
   */
  def withStatus(status: Status): Output[A] =
    withMeta(meta.copy(status = status))

  /**
   * Adds a given `header` to this [[Output]].
   */
  def withHeader(header: (String, String)): Output[A] =
    withMeta(meta.copy(headers = headers + header))

  /**
   * Adds a given `cookie` to this [[Output]].
   */
  def withCookie(cookie: Cookie): Output[A] =
    withMeta(meta.copy(cookies = cookies :+ cookie))
}

object Output {

  /**
   * A data type representing an HTTP response metadata shared between different types of
   * [[Output]]s.
   */
  private[finch] case class Meta(
    status: Status = Status.Ok,
    charset: Option[Charset] = Option.empty,
    headers: Map[String, String] = Map.empty[String, String],
    cookies: Seq[Cookie] = Seq.empty[Cookie]
  )

  /**
   * Creates a successful [[Output]] that wraps a payload `value` with given `status`.
   */
  final def payload[A](value: A, status: Status = Status.Ok): Output[A] =
    Payload(value, Meta(status = status))

  /**
   * Creates a failure [[Output]] that wraps an exception `cause` causing this.
   */
  final def failure[A](cause: Exception, status: Status = Status.BadRequest): Output[A] =
    Failure(cause, Meta(status = status))

  /**
   * Creates an empty [[Output]] of given `status`.
   */
  final def empty[A](status: Status): Output[A] =
    Empty(Meta(status = status))

  /**
   * Creates a unit/empty [[Output]] (i.e., `Output[Unit]`) of given `status`.
   */
  final def unit(status: Status): Output[Unit] = Empty(Meta(status = status))

  /**
   * An [[Output]] with `None` as payload.
   */
  val None: Output[Option[Nothing]] = Output.payload(Option.empty[Nothing])

  /**
   * A successful [[Output]] that captures a payload `value`.
   */
  private[finch] final case class Payload[A](value: A, meta: Meta) extends Output[A] {
    override protected def withMeta(meta: Meta): Output[A] = copy(meta = meta)
  }

  /**
   * A failure [[Output]] that captures an  [[Exception]] explaining why it's not a payload
   * or an empty response.
   */
  private[finch] final case class Failure(cause: Exception, meta: Meta) extends Output[Nothing] {
    override protected def withMeta(meta: Meta): Output[Nothing] = copy(meta = meta)
    override def value: Nothing = throw cause
  }

  /**
   * An empty [[Output]] that does not capture any payload.
   */
  private[finch] final case class Empty(meta: Meta) extends Output[Nothing] {
    override protected def withMeta(meta: Meta): Output[Nothing] = copy(meta = meta)
    override def value: Nothing = throw new IllegalStateException("empty output")
  }

  implicit def outputEq[A](implicit A: Eq[A]): Eq[Output[A]] = Eq.instance {
    case (Payload(av, am), Payload(bv, bm)) => A.eqv(av, bv) && am == bm
    case (Failure(ac, am), Failure(bc, bm)) => ac == bc && am == bm
    case (Empty(am), Empty(bm)) => am == bm
    case (_, _) => false
  }

  /**
   * Exposes an API for testing [[Endpoint]]s.
   */
  implicit class EndpointResultOps[A](val o: Endpoint.Result[A]) extends AnyVal {

    /**
     * Queries an [[Output]] wrapped with [[Try]] (indicating if the [[Future]] is failed).
     *
     * @note This method is blocking and awaits on the underlying [[Future]] with the upper
     *       bound of 10 seconds.
     *
     * @return `Some(output)` if this endpoint was matched on a given input,
     *         `None` otherwise.
     */
    def tryOutput: Option[Try[Output[A]]] =
      o.map({ case (_, oa) => Await.result(oa.liftToTry.run, Duration.fromSeconds(10)) })

    /**
     * Queries a value from the [[Output]] wrapped with [[Try]] (indicating if either the
     * [[Future]] is failed or [[Output]] wasn't a payload).
     *
     * @note This method is blocking and awaits on the underlying [[Future]] with the upper
     *       bound of 10 seconds.
     *
     * @return `Some(value)` if this endpoint was matched on a given input,
     *         `None` otherwise.
     */
    def tryValue: Option[Try[A]] =
      tryOutput.map(toa => toa.flatMap(oa => Try(oa.value)))

    /**
     * Queries an [[Output]] of the [[Endpoint]] result or throws an exception if an underlying
     * [[Future]] is failed.
     *
     * @note This method is blocking and awaits on the underlying [[Future]] with the upper
     *       bound of 10 seconds.
     *
     * @return `Some(output)` if this endpoint was matched on a given input,
     *         `None` otherwise.
     */
    def output: Option[Output[A]] = tryOutput.map(tao => tao.get)

    /**
     * Queries the value from the [[Output]] or throws an exception if either an underlying
     * [[Future]] is failed or [[Output]] wasn't a payload.
     *
     * @note This method is blocking and awaits on the underlying [[Future]] with the upper
     *       bound of 10 seconds.
     *
     * @return `Some(value)` if this endpoint was matched on a given input,
     *         `None` otherwise.
     */
    def value: Option[A] = output.map(oa => oa.value)

    /**
     * Returns the remainder of the [[Input]] after an [[Endpoint]] is matched.
     *
     * @return `Some(remainder)` if this endpoint was matched on a given input,
     *         `None` otherwise.
     */
    def remainder: Option[Input] = o.map(_._1)
  }

  implicit class OutputOps[A](val o: Output[A]) extends AnyVal {

    /**
     * Converts this [[Output]] to the HTTP response of the given `version`.
     */
    def toResponse[CT <: String](version: Version = Version.Http11)(implicit
      tr: ToResponse.Aux[A, CT],
      tre: ToResponse.Aux[Exception, CT]
    ): Response = {
      val rep = o match {
        case Output.Payload(v, m) => tr(v, m.charset.getOrElse(StandardCharsets.UTF_8))
        case Output.Failure(x, m) => tre(x, m.charset.getOrElse(StandardCharsets.UTF_8))
        case Output.Empty(_) => Response()
      }

      rep.status = o.status
      rep.version = version

      o.headers.foreach { case (k, v) => rep.headerMap.set(k, v) }
      o.cookies.foreach(rep.cookies.add)
      o.charset.foreach { c =>
        if (!rep.content.isEmpty) {
          rep.charset = c.displayName.toLowerCase
        }
      }

      rep
    }
  }
}
