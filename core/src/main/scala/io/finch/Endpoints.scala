package io.finch

import java.util.UUID

import cats.Eval
import cats.data.StateT
import cats.std.option._
import cats.syntax.option._
import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.http.{Cookie, Method, Request}
import com.twitter.finagle.http.exp.Multipart.FileUpload
import com.twitter.finagle.netty3.ChannelBufferBuf
import com.twitter.io.{Buf, Charsets}
import com.twitter.util.{Base64StringEncoder, Future, Try}
import io.finch.internal.TooFastString
import shapeless._

/**
 * A collection of [[Endpoint]] combinators.
 */
trait Endpoints {

  private[this] val hnilFutureOutput: Eval[Future[Output[HNil]]] =
    Eval.now(Future.value(Output.payload(HNil)))

  type Endpoint0 = Endpoint[HNil]
  type Endpoint2[A, B] = Endpoint[A :: B :: HNil]
  type Endpoint3[A, B, C] = Endpoint[A :: B :: C :: HNil]

  private[finch] class Matcher(s: String) extends Endpoint[HNil] {
    override val embed: Endpoint.State[HNil] = StateT(input =>
      input.headOption.flatMap {
        case `s` => (input.drop(1), hnilFutureOutput).some
        case _ => none
      }
    )
    override def toString = s
  }

  implicit def stringToMatcher(s: String): Endpoint0 = new Matcher(s)
  implicit def intToMatcher(i: Int): Endpoint0 = new Matcher(i.toString)
  implicit def booleanToMatcher(b: Boolean): Endpoint0 = new Matcher(b.toString)

  /**
   * An universal extractor that extracts some value of type `A` if it's possible to fetch the value
   * from the string.
   */
  private[finch] case class Extractor[A](name: String, f: String => Option[A]) extends Endpoint[A] {
    override val embed: Endpoint.State[A] = StateT(input =>
      for {
        ss <- input.headOption
        aa <- f(ss)
      } yield (input.drop(1), Eval.now(Future.value(Output.payload(aa))))
    )

    def apply(n: String): Endpoint[A] = copy[A](name = n)
    override def toString: String = s":$name"
  }

  private[finch] case class StringExtractor(name: String) extends Endpoint[String] {
    override val embed: Endpoint.State[String] = StateT(input =>
      input.headOption.map(s => (input.drop(1), Eval.now(Future.value(Output.payload(s)))))
    )

    def apply(n: String): Endpoint[String] = copy(name = n)
    override def toString: String = s":$name"
  }

  /**
   * An extractor that extracts a value of type `Seq[A]` from the tail of the route.
   */
  private[finch] case class TailExtractor[A](
    name: String,
    f: String => Option[A]
  ) extends Endpoint[Seq[A]] {
    override val embed: Endpoint.State[Seq[A]] = StateT(input =>
      (input.copy(path = Nil), Eval.now(Future.value(Output.payload(for {
        s <- input.path
        a <- f(s)
      } yield a)))).some
    )

    def apply(n: String): Endpoint[Seq[A]] = copy[A](name = n)

    override def toString: String = s":$name*"
  }

  private[this] def extractUUID(s: String): Option[UUID] =
    if (s.length != 36) None
    else try Some(UUID.fromString(s)) catch { case _: Exception => None }

  private[this] def result[A](i: Input, a: A): (Input, Eval[Future[Output[A]]]) =
    (i.drop(1), Eval.now(Future.value(Output.payload(a))))

  /**
   * A matching [[Endpoint]] that reads a string value from the current path segment.
   *
   * @note This is an experimental API and might be removed without any notice.
   */
  val path: Endpoint[String] = new Endpoint[String] {
    override val embed: Endpoint.State[String] = StateT(input =>
      input.headOption.map(s => result(input, s))
    )

    override def toString: String = ":path"
  }

  /**
   * A matching [[Endpoint]] that reads a value of type `A` (using the implicit
   * [[internal.Capture]] instances defined for `A`) from the current path segment.
   */
  def path[A](implicit c: internal.Capture[A]): Endpoint[A] = new Endpoint[A] {
    override val embed: Endpoint.State[A] = StateT(input =>
      for {
        ss <- input.headOption
        aa <- c(ss)
      } yield result(input, aa)
    )

    override def toString: String = ":path"
  }
  /**
   * A matching [[Endpoint]] that reads an integer value from the current path segment.
   */
  object int extends Extractor("int", _.tooInt)

  /**
   * A matching [[Endpoint]] that reads an integer tail from the current path segment.
   */
  object ints extends TailExtractor("int", _.tooInt)

  /**
   * A matching [[Endpoint]] that reads a long value from the current path segment.
   */
  object long extends Extractor("long", _.tooLong)

  /**
   * A matching [[Endpoint]] that reads a long tail from the current path segment.
   */
  object longs extends TailExtractor("long", _.tooLong)

  /**
   * A matching [[Endpoint]] that reads a string value from the current path segment.
   */
  object string extends StringExtractor("string")

  /**
   * A matching [[Endpoint]] that reads a string tail from the current path segment.
   */
  object strings extends TailExtractor("string", s => Some(s))

  /**
   * A matching [[Endpoint]] that reads a boolean value from the current path segment.
   */
  object boolean extends Extractor("boolean", _.tooBoolean)

  /**
   * A matching [[Endpoint]] that reads a boolean tail from the current path segment.
   */
  object booleans extends TailExtractor("boolean", _.tooBoolean)

  /**
   * A matching [[Endpoint]] that reads an UUID value from the current path segment.
   */
  object uuid extends Extractor("uuid", extractUUID)

  /**
   * A matching [[Endpoint]] that reads an UUID tail from the current path segment.
   */
  object uuids extends TailExtractor("uuid", extractUUID)

  /**
   * An [[Endpoint]] that skips all path segments.
   */
  object * extends Endpoint[HNil] {
    override val embed: Endpoint.State[HNil] = StateT(input =>
      (input.copy(path = Nil), hnilFutureOutput).some
    )
    override def toString: String = "*"
  }

  /**
   * An identity [[Endpoint]].
   */
  object / extends Endpoint[HNil] {
    override val embed: Endpoint.State[HNil] = StateT.pure(hnilFutureOutput)
    override def toString: String = ""
  }

  private[this] def method[A](m: Method)(e: Endpoint[A]): Endpoint[A] = new Endpoint[A] {
    override val embed: Endpoint.State[A] = StateT(input =>
      if (input.request.method == m) e(input)
      else none
    )

    override def toString: String = s"${m.toString().toUpperCase} /${e.toString}"
  }

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `GET` and the underlying
   * endpoint succeeds on it.
   */
  def get[A]: Endpoint[A] => Endpoint[A] = method(Method.Get)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `POST` and the underlying
   * endpoint succeeds on it.
   */
  def post[A]: Endpoint[A] => Endpoint[A] = method(Method.Post)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `PATCH` and the underlying
   * endpoint succeeds on it.
   */
  def patch[A]: Endpoint[A] => Endpoint[A] = method(Method.Patch)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `DELETE` and the
   * underlying endpoint succeeds on it.
   */
  def delete[A]: Endpoint[A] => Endpoint[A] = method(Method.Delete)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `HEAD` and the underlying
   * endpoint succeeds on it.
   */
  def head[A]: Endpoint[A] => Endpoint[A] = method(Method.Head)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `OPTIONS` and the
   * underlying endpoint succeeds on it.
   */
  def options[A]: Endpoint[A] => Endpoint[A] = method(Method.Options)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `PUT` and the underlying
   * endpoint succeeds on it.
   */
  def put[A]: Endpoint[A] => Endpoint[A] = method(Method.Put)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `CONNECT` and the
   * underlying endpoint succeeds on it.
   */
  def connect[A]: Endpoint[A] => Endpoint[A] = method(Method.Connect)

  /**
   * A combinator that wraps the given [[Endpoint]] with additional check of the HTTP method. The
   * resulting [[Endpoint]] succeeds on the request only if its method is `TRACE` and the underlying
   * router endpoint on it.
   */
  def trace[A]: Endpoint[A] => Endpoint[A] = method(Method.Trace)

  // Helper functions.
  private[this] def requestParam(param: String)(req: Request): Option[String] =
    req.params.get(param)
      .orElse(req.multipart.flatMap(m => m.attributes.get(param).flatMap(_.headOption)))

  private[this] def requestParams(params: String)(req: Request): Seq[String] =
    req.params.getAll(params).toList.flatMap(_.split(","))

  private[this] def requestHeader(header: String)(req: Request): Option[String] =
    req.headerMap.get(header)

  private[this] def requestCookie(cookie: String)(req: Request): Option[Cookie] =
    req.cookies.get(cookie)

  private[this] def requestBody(req: Request): Option[String] =
    req.contentLength match {
      case Some(n) if n > 0 =>
        val buffer = ChannelBufferBuf.Owned.extract(req.content)
        // Note: We usually have an array underneath the ChannelBuffer (at least on Netty 3).
        // This check is mostly about a safeguard.
        // TODO: Use proper charset
        if (buffer.hasArray) Some(new String(buffer.array(), 0, buffer.readableBytes(), "UTF-8"))
        else buffer.toString(Charsets.Utf8).some
      case _ => None
    }

  private[this] def requestUpload(upload: String)(req: Request): Option[FileUpload] =
    Try(req.multipart).getOrElse(none).flatMap(m => m.files.get(upload).flatMap(fs => fs.headOption))

  private[this] def option[A](item: items.RequestItem)(f: Request => A): Endpoint[A] =
    Endpoint.embed(item)(input =>
      (input, Eval.later(Future.value(Output.payload(f(input.request))))).some
    )

  private[this] def exists[A](item: items.RequestItem)(f: Request => Option[A]): Endpoint[A] =
    Endpoint.embed(item)(input =>
      f(input.request).map(s => (input, Eval.now(Future.value(Output.payload(s)))))
    )

  private[this] def matches[A]
    (item: items.RequestItem)
    (p: Request => Boolean)
    (f: Request => A): Endpoint[A] = Endpoint.embed(item)(input =>
      if (p(input.request)) (input, Eval.later(Future.value(Output.payload(f(input.request))))).some
      else none
    )

  /**
   * An evaluating [[Endpoint]] that reads an optional query-string param `name` from the request
   * into an `Option`.
   */
  def paramOption(name: String): Endpoint[Option[String]] =
    option(items.ParamItem(name))(requestParam(name)).noneIfEmpty

  /**
   * An evaluating [[Endpoint]] that reads a required query-string param `name` from the
   * request or raises an [[Error.NotPresent]] exception when the param is missing; an
   * [[Error.NotValid]] exception is the param is empty.
   */
  def param(name: String): Endpoint[String] =
    paramOption(name).failIfNone.shouldNot(beEmpty)

  /**
   * A matching [[Endpoint]] that only matches the requests that contain a given query-string
   * param `name`.
   */
  def paramExists(name: String): Endpoint[String] =
    exists(items.ParamItem(name))(requestParam(name))

  /**
   * An evaluating [[Endpoint]] that reads an optional (in a meaning that a resulting
   * `Seq` may be empty) multi-value query-string param `name` from the request into a `Seq`.
   */
  def params(name: String): Endpoint[Seq[String]] =
    option(items.ParamItem(name))(i => requestParams(name)(i).filter(_.nonEmpty))

  /**
   * An evaluating [[Endpoint]] that reads a required (in a meaning that a resulting `Seq` will have
   * at least one element) multi-value query-string param `name` from the request into a `Seq` or
   * raises a [[Error.NotPresent]] exception when the params are missing or empty.
   */
  def paramsNonEmpty(name: String): Endpoint[Seq[String]] =
    option(items.ParamItem(name))(requestParams(name)).mapAsync({
      case Nil => Future.exception(Error.NotPresent(items.ParamItem(name)))
      case unfiltered => Future.value(unfiltered.filter(_.nonEmpty))
    }).shouldNot("be empty")(_.isEmpty)

  /**
   * An evaluating [[Endpoint]] that reads a required HTTP header `name` from the request or raises
   * an [[Error.NotPresent]] exception when the header is missing.
   */
  def header(name: String): Endpoint[String] =
    option(items.HeaderItem(name))(requestHeader(name)).failIfNone.shouldNot(beEmpty)

  /**
   * An evaluating [[Endpoint]] that reads an optional HTTP header `name` from the request into an
   * `Option`.
   */
  def headerOption(name: String): Endpoint[Option[String]] =
    option(items.HeaderItem(name))(requestHeader(name)).noneIfEmpty

  /**
   * A matching [[Endpoint]] that only matches the requests that contain a given header `name`.
   */
  def headerExists(name: String): Endpoint[String] =
    exists(items.HeaderItem(name))(requestHeader(name))

  /**
   * An evaluating [[Endpoint]] that reads a binary request body, interpreted as a `Array[Byte]`,
   * into an `Option`. The returned [[Endpoint]] only matches non-chunked (non-streamed) requests.
   */
  val binaryBodyOption: Endpoint[Option[Array[Byte]]] =
    matches(items.BodyItem)(!_.isChunked)(req =>
      req.contentLength match {
        case Some(n) if n > 0 => Buf.ByteArray.Shared.extract(req.content).some
        case _ => none
      }
    )

  /**
   * An evaluating [[Endpoint]] that reads a required binary request body, interpreted as an
   * `Array[Byte]`, or throws a [[Error.NotPresent]] exception. The returned [[Endpoint]] only
   * matches non-chunked (non-streamed) requests.
   */
  val binaryBody: Endpoint[Array[Byte]] = binaryBodyOption.failIfNone

  /**
   * An evaluating [[Endpoint]] that reads an optional request body, interpreted as a `String`, into
   * an `Option`. The returned [[Endpoint]] only matches non-chunked (non-streamed) requests.
   */
  val bodyOption: Endpoint[Option[String]] = matches(items.BodyItem)(!_.isChunked)(requestBody)

  /**
   * An evaluating[[Endpoint]] that reads the required request body, interpreted as a `String`, or
   * throws an [[Error.NotPresent]] exception. The returned [[Endpoint]] only matches non-chunked
   * (non-streamed) requests.
   */
  val body: Endpoint[String] = bodyOption.failIfNone

  /**
   * An evaluating [[Endpoint]] that reads a required chunked streaming binary body, interpreted as
   * an `AsyncStream[Buf]`. The returned [[Endpoint]] only matches chunked (streamed) requests.
   */
  val asyncBody: Endpoint[AsyncStream[Buf]] =
    matches(items.BodyItem)(_.isChunked)(req => AsyncStream.fromReader(req.reader))

  /**
   * An evaluating [[Endpoint]] that reads an optional HTTP cookie from the request into an
   * `Option`.
   */
  def cookieOption(name: String): Endpoint[Option[Cookie]] =
    option(items.CookieItem(name))(requestCookie(name))

  /**
   * An evaluating [[Endpoint]] that reads a required cookie from the request or raises an
   * [[Error.NotPresent]] exception when the cookie is missing.
   */
  def cookie(name: String): Endpoint[Cookie] = cookieOption(name).failIfNone

  /**
   * An evaluating[[Endpoint]] that reads an optional file upload from a multipart/form-data request
   * into an `Option`.
   */
  def fileUploadOption(name: String): Endpoint[Option[FileUpload]] =
    matches(items.ParamItem(name))(!_.isChunked)(requestUpload(name))

  /**
   * An evaluating [[Endpoint]] that reads a required file upload from a multipart/form-data
   * request.
   */
  def fileUpload(name: String): Endpoint[FileUpload] = fileUploadOption(name).failIfNone

  /**
   * An [[Exception]] representing a failed authorization with [[BasicAuth]].
   */
  object BasicAuthFailed extends Exception {
    override def getMessage: String = "Wrong credentials"
  }

  /**
   * Maintains Basic HTTP Auth for an arbitrary [[Endpoint]].
   */
  case class BasicAuth(user: String, password: String) {
    private[this] val userInfo = s"$user:$password"
    private[this] val expected = "Basic " + Base64StringEncoder.encode(userInfo.getBytes)

    def apply[A](e: Endpoint[A]): Endpoint[A] = new Endpoint[A] {
      private[this] val failedOutput: Eval[Future[Output[A]]] =
        Eval.now(Future.value(Unauthorized(BasicAuthFailed)))

      override val embed: Endpoint.State[A] = StateT(input =>
        input.request.authorization.flatMap {
          case `expected` => e(input)
          case _ => (input.copy(path = Seq.empty), failedOutput).some
        }
      )

      override def toString: String = s"BasicAuth($e)"
    }
  }

  private[finch] val beEmpty: ValidationRule[String] = ValidationRule("be empty")(_.isEmpty)
}
