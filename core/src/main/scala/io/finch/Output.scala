package io.finch

import com.twitter.finagle.http.{Cookie, Response, Status, Version}
import com.twitter.util.{Await, Future, Try}
import io.finch.internal.ToResponse

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
      )
      )

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
   * Overrides the status code of this [[Output]].
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

  implicit class EndpointResultOps[A](val o: Endpoint.Result[A]) extends AnyVal {
    private[finch] def poll: Option[Try[A]] = o.flatMap(_._2.value.poll.map(_.map(_.value)))
    private[finch] def output: Option[Output[A]] = o.map({ case (_, oa) => Await.result(oa.value) })
    private[finch] def value: Option[A] = output.map(oa => oa.value)
    private[finch] def remainder: Option[Input] = o.map(_._1)
  }

  implicit class OutputOps[A](val o: Output[A]) extends AnyVal {

    /**
     * Converts this [[Output]] to the HTTP response of the given `version`.
     */
    def toResponse[CT <: String](version: Version = Version.Http11)(implicit
      payloadToResponse: ToResponse.Aux[A, CT],
      failureToResponse: ToResponse.Aux[Exception, CT]
    ): Response = {
      val rep = o match {
        case Output.Payload(v, _) => payloadToResponse(v)
        case Output.Failure(x, _) => failureToResponse(x)
        case Output.Empty(_) => Response()
      }
      rep.version = version
      rep.status = o.status
      o.headers.foreach { case (k, v) => rep.headerMap.set(k, v) }
      o.cookies.foreach(rep.cookies.add)

      rep
    }
  }
}
