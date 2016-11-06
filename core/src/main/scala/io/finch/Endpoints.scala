package io.finch

import cats.data.NonEmptyList
import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.http.{Cookie, Method, Request}
import com.twitter.finagle.http.exp.Multipart.FileUpload
import com.twitter.finagle.netty3.ChannelBufferBuf
import com.twitter.io.Buf
import com.twitter.util.{Base64StringEncoder, Future, Try}
import io.catbird.util.Rerunnable
import io.finch.internal._
import java.util.UUID
import shapeless._

/**
 * A collection of [[Endpoint]] combinators.
 */
trait Endpoints {

  private[this] val hnilFutureOutput: Rerunnable[Output[HNil]] = new Rerunnable[Output[HNil]] {
    override val run = Future.value(Output.payload(HNil))
  }

  type Endpoint0 = Endpoint[HNil]
  type Endpoint2[A, B] = Endpoint[A :: B :: HNil]
  type Endpoint3[A, B, C] = Endpoint[A :: B :: C :: HNil]

  /**
   * An universal [[Endpoint]] that matches the given string.
   */
  private[finch] class Matcher(s: String) extends Endpoint[HNil] {
    def apply(input: Input): Endpoint.Result[HNil] =
      input.headOption.flatMap {
        case `s` => Some(input.drop(1) -> hnilFutureOutput)
        case _ => None
      }

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
    def apply(input: Input): Endpoint.Result[A] =
      for {
        ss <- input.headOption
        aa <- f(ss)
      } yield input.drop(1) -> new Rerunnable[Output[A]] {
        override def run = Future.value(Output.payload(aa))
      }

    def apply(n: String): Endpoint[A] = copy[A](name = n)

    override def toString: String = s":$name"
  }

  private[finch] case class StringExtractor(name: String) extends Endpoint[String] {
    def apply(input: Input): Endpoint.Result[String] =
      input.headOption.map(s => input.drop(1) -> new Rerunnable[Output[String]] {
        override def run = Future.value(Output.payload(s))
      })

    def apply(n: String): Endpoint[String] = copy(name = n)

    override def toString: String = s":$name"
  }

  /**
   * An extractor that extracts a value of type `Seq[A]` from the tail of the route.
   */
  private[finch] case class TailExtractor[A](
      name: String,
      f: String => Option[A]) extends Endpoint[Seq[A]] {
    def apply(input: Input): Endpoint.Result[Seq[A]] =
      Some(input.copy(path = Nil) -> new Rerunnable[Output[Seq[A]]] {
        override def run = Future.value(Output.payload(for {
          s <- input.path
          a <- f(s)
        } yield a))
      })

    def apply(n: String): Endpoint[Seq[A]] = copy[A](name = n)

    override def toString: String = s":$name*"
  }

  private[this] def extractUUID(s: String): Option[UUID] =
    if (s.length != 36) None
    else try Some(UUID.fromString(s)) catch { case _: Exception => None }

  private[this] def result[A](i: Input, a: A): (Input, Rerunnable[Output[A]]) =
    i.drop(1) -> new Rerunnable[Output[A]] {
      override def run = Future.value(Output.payload(a))
    }

  /**
   * A matching [[Endpoint]] that reads a string value from the current path segment.
   *
   * @note This is an experimental API and might be removed without any notice.
   */
  val path: Endpoint[String] = new Endpoint[String] {
    def apply(input: Input): Endpoint.Result[String] =
      input.headOption.map(s => result(input, s))

    override def toString: String = ":path"
  }

  /**
   * A matching [[Endpoint]] that reads a value of type `A` (using the implicit
   * [[DecodePath]] instances defined for `A`) from the current path segment.
   */
  def path[A](implicit c: DecodePath[A]): Endpoint[A] = new Endpoint[A] {
    def apply(input: Input): Endpoint.Result[A] = for {
      ss <- input.headOption
      aa <- c(ss)
    } yield result(input, aa)

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
    def apply(input: Input): Endpoint.Result[HNil] =
      Some(input.copy(path = Nil) -> hnilFutureOutput)

    override def toString: String = "*"
  }

  /**
   * An identity [[Endpoint]].
   */
  object / extends Endpoint[HNil] {
    def apply(input: Input): Endpoint.Result[HNil] =
      Some(input -> hnilFutureOutput)

    override def toString: String = ""
  }

  private[this] def method[A](m: Method)(r: Endpoint[A]): Endpoint[A] = new Endpoint[A] {
    def apply(input: Input): Endpoint.Result[A] =
      if (input.request.method == m) r(input)
      else None

    override def toString: String = s"${m.toString().toUpperCase} /${r.toString}"
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
    req.params.getAll(params).toList

  private[this] def requestHeader(header: String)(req: Request): Option[String] =
    req.headerMap.get(header)

  private[this] def requestCookie(cookie: String)(req: Request): Option[Cookie] =
    req.cookies.get(cookie)

  private[this] val someEmptyString = Some("")
  private[this] def requestBodyString(req: Request): Option[String] =
    req.contentLength match {
      case Some(0) => someEmptyString
      case Some(_) =>
        val buffer = ChannelBufferBuf.Owned.extract(req.content)
        val charset = req.charsetOrUtf8
        // Note: We usually have an array underneath the ChannelBuffer (at least on Netty 3).
        // This check is mostly about a safeguard.
        if (buffer.hasArray) Some(new String(buffer.array(), 0, buffer.readableBytes(), charset))
        else Some(buffer.toString(charset))
      case None => None
    }

  private[this] val someEmptyBuf = Some(Buf.Empty)
  private[this] def requestBody(req: Request): Option[Buf] =
    req.contentLength match {
      case Some(0) => someEmptyBuf
      case Some(_) => Some(req.content)
      case None => None
    }

  private[this] val someEmptyByteArray = Some(Array.empty[Byte])
  private[this] def requestBodyByteArray(req: Request): Option[Array[Byte]] =
    req.contentLength match {
      case Some(0) => someEmptyByteArray
      case Some(_) => Some(Buf.ByteArray.Shared.extract(req.content))
      case None => None
    }

  private[this] def requestUpload(upload: String)(req: Request): Option[FileUpload] =
    Try(req.multipart).getOrElse(None).flatMap(m => m.files.get(upload).flatMap(fs => fs.headOption))

  private[this] def option[A](item: items.RequestItem)(f: Request => A): Endpoint[A] =
    Endpoint.embed(item)(input =>
      Some(input -> new Rerunnable[Output[A]] {
        override def run = Future.value(Output.payload(f(input.request)))
      }))

  private[this] def exists[A](item: items.RequestItem)(f: Request => Option[A]): Endpoint[A] =
    Endpoint.embed(item)(input =>
      f(input.request).map(s => input -> new Rerunnable[Output[A]] {
        override def run = Future.value(Output.payload(s))
      })
    )

  private[this] def matches[A]
    (item: items.RequestItem)
    (p: Request => Boolean)
    (f: Request => A): Endpoint[A] = Endpoint.embed(item)(input =>
      if (p(input.request)) Some(input -> new Rerunnable[Output[A]] {
        override def run = Future.value(Output.payload(f(input.request)))
      }) else None
    )

  /**
   * A root [[Endpoint]] that always matches and extracts the current request.
   */
  val root: Endpoint[Request] = option(items.MultipleItems)(identity)

  /**
   * An evaluating [[Endpoint]] that reads an optional query-string param `name` from the request
   * into an `Option`.
   */
  def paramOption(name: String): Endpoint[Option[String]] =
    option(items.ParamItem(name))(requestParam(name))

  /**
   * An evaluating [[Endpoint]] that reads a required query-string param `name` from the
   * request or raises an [[Error.NotPresent]] exception when the param is missing; an
   * [[Error.NotValid]] exception is the param is empty.
   */
  def param(name: String): Endpoint[String] =
    paramOption(name).failIfNone

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
    option(items.ParamItem(name))(i => requestParams(name)(i))

  /**
   * An evaluating [[Endpoint]] that reads a required (in a meaning that a resulting `Seq` will have
   * at least one element) multi-value query-string param `name` from the request into a `Seq` or
   * raises a [[Error.NotPresent]] exception when the params are missing or empty.
   */
  @deprecated("Use paramsNel and NonEmptyList instead", "0.11")
  def paramsNonEmpty(name: String): Endpoint[Seq[String]] =
    option(items.ParamItem(name))(requestParams(name)).mapAsync({
      case Nil => Future.exception(Error.NotPresent(items.ParamItem(name)))
      case unfiltered => Future.value(unfiltered.filter(_.nonEmpty))
    }).shouldNot("be empty")(_.isEmpty)

  /**
   * An evaluating [[Endpoint]] that reads a required multi-value query-string param `name`
   * from the request into a `NonEmptyList` or raises a [[Error.NotPresent]] exception
   * when the params are missing or empty.
   */
  def paramsNel(name: String): Endpoint[NonEmptyList[String]] =
    option(items.ParamItem(name))(requestParams(name)).mapAsync { values =>
      values.filter(_.nonEmpty).toList match {
        case Nil => Future.exception(Error.NotPresent(items.ParamItem(name)))
        case seq => Future.value(NonEmptyList(seq.head, seq.tail))
      }
    }

  /**
   * An evaluating [[Endpoint]] that reads a required HTTP header `name` from the request or raises
   * an [[Error.NotPresent]] exception when the header is missing.
   */
  def header(name: String): Endpoint[String] =
    option(items.HeaderItem(name))(requestHeader(name)).failIfNone

  /**
   * An evaluating [[Endpoint]] that reads an optional HTTP header `name` from the request into an
   * `Option`.
   */
  def headerOption(name: String): Endpoint[Option[String]] =
    option(items.HeaderItem(name))(requestHeader(name))

  /**
   * A matching [[Endpoint]] that only matches the requests that contain a given header `name`.
   */
  def headerExists(name: String): Endpoint[String] =
    exists(items.HeaderItem(name))(requestHeader(name))

  /**
   * An evaluating [[Endpoint]] that reads a binary request body, interpreted as a `Array[Byte]`,
   * into an `Option`. The returned [[Endpoint]] only matches non-chunked (non-streamed) requests.
   */
  val bodyByteArrayOption: Endpoint[Option[Array[Byte]]] =
    matches(items.BodyItem)(!_.isChunked)(requestBodyByteArray)

  /**
   * An evaluating [[Endpoint]] that reads a required binary request body, interpreted as an
   * `Array[Byte]`, or throws a [[Error.NotPresent]] exception. The returned [[Endpoint]] only
   * matches non-chunked (non-streamed) requests.
   */
  val bodyByteArray: Endpoint[Array[Byte]] = bodyByteArrayOption.failIfNone

  /**
   * An evaluating [[Endpoint]] that reads a binary request body, interpreted as a `Array[Byte]`,
   * into an `Option`. The returned [[Endpoint]] only matches non-chunked (non-streamed) requests.
   */
  @deprecated("Use bodyByteArrayOption instead.", "0.11")
  val binaryBodyOption: Endpoint[Option[Array[Byte]]] = bodyByteArrayOption

  /**
   * An evaluating [[Endpoint]] that reads a required binary request body, interpreted as an
   * `Array[Byte]`, or throws a [[Error.NotPresent]] exception. The returned [[Endpoint]] only
   * matches non-chunked (non-streamed) requests.
   */
  @deprecated("Use bodyByteArray instead.", "0.11")
  val binaryBody: Endpoint[Array[Byte]] = bodyByteArray

  /**
   * An evaluating [[Endpoint]] that reads an optional request body, interpreted as a `String`, into
   * an `Option`. The returned [[Endpoint]] only matches non-chunked (non-streamed) requests.
   */
  val bodyStringOption: Endpoint[Option[String]] =
    matches(items.BodyItem)(!_.isChunked)(requestBodyString)

  /**
   * An evaluating [[Endpoint]] that reads the required request body, interpreted as a `String`, or
   * throws an [[Error.NotPresent]] exception. The returned [[Endpoint]] only matches non-chunked
   * (non-streamed) requests.
   */
  val bodyString: Endpoint[String] = bodyStringOption.failIfNone


  /**
   * An [[Endpoint]] that reads an optional request body, interpreted as [[Buf]], into
   * an `Option`. The returned [[Endpoint]] only matches non-chunked (non-streamed) requests.
   */
  val bodyOption: Endpoint[Option[Buf]] =
    matches(items.BodyItem)(!_.isChunked)(requestBody)

  /**
   * An [[Endpoint]] that reads the required request body, interpreted as [[Buf]], or
   * throws an [[Error.NotPresent]] exception. The returned [[Endpoint]] only matches non-chunked
   * (non-streamed) requests.
   */
  val body: Endpoint[Buf] = bodyOption.failIfNone

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
  case class BasicAuth(realm: String)(authenticate: (String, String) => Future[Boolean]) {
    def apply[A](e: Endpoint[A]): Endpoint[A] = new Endpoint[A] {
      private[this] val unauthorized = new Rerunnable[Output[A]] {
        override def run = Future.value(Unauthorized(BasicAuthFailed)
          .withHeader("WWW-Authenticate" -> s"""Basic realm="$realm""""))
      }

      def apply(input: Input): Endpoint.Result[A] =
        e(input).map { case (input, output) =>
          input -> authenticated(input).flatMap(if (_) output else unauthorized)
        }

      private[this] def authenticated(input: Input): Rerunnable[Boolean] =
        Rerunnable.fromFuture(
          input.request.authorization
            .flatMap(parse)
            .map(authenticate.tupled)
            .getOrElse(Future.False))

      private[this] def parse(authorization: String): Option[(String, String)] = for {
        (scheme, params) <- parseAuthorization(authorization)
        if scheme == "basic"
        (username, password) <- parseCredentials(params)
      } yield (username, password)

      private[this] def parseAuthorization(authorization: String): Option[(String, String)] =
        authorization.split(" ", 2) match {
          case Array(scheme, params) => Some((scheme.toLowerCase, params))
          case _ => None
        }

      private[this] def parseCredentials(params: String): Option[(String, String)] =
        new String(Base64StringEncoder.decode(params)).split(":", 2) match {
          case Array(username, password) => Some((username, password))
          case _ => None
        }

      override def toString: String = s"""BasicAuth(realm="$realm", $e)"""
    }
  }
}
