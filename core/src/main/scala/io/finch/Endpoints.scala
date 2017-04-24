package io.finch

import cats.data.NonEmptyList
import com.twitter.finagle.http.{Cookie, Method, Request}
import com.twitter.finagle.http.exp.Multipart.FileUpload
import com.twitter.util.{Future, Try}
import io.catbird.util.Rerunnable
import io.finch.endpoint._
import io.finch.internal._
import java.util.UUID
import shapeless._

/**
 * A collection of [[Endpoint]] combinators.
 */
trait Endpoints extends Bodies {

  type Endpoint0 = Endpoint[HNil]
  type Endpoint2[A, B] = Endpoint[A :: B :: HNil]
  type Endpoint3[A, B, C] = Endpoint[A :: B :: C :: HNil]

  /**
   * An universal [[Endpoint]] that matches the given string.
   */
  private[finch] class Matcher(s: String) extends Endpoint[HNil] {
    final def apply(input: Input): Endpoint.Result[HNil] = input.route match {
      case `s` +: rest => EndpointResult.Matched(input.withRoute(rest), Rs.OutputHNil)
      case _ => EndpointResult.Skipped
    }

    override final def toString: String = s
  }

  implicit def stringToMatcher(s: String): Endpoint0 = new Matcher(s)
  implicit def intToMatcher(i: Int): Endpoint0 = new Matcher(i.toString)
  implicit def booleanToMatcher(b: Boolean): Endpoint0 = new Matcher(b.toString)

  /**
   * An universal extractor that extracts some value of type `A` if it's possible to fetch the value
   * from the string.
   */
  private[finch] case class Extractor[A](name: String, f: String => Option[A]) extends Endpoint[A] {
    final def apply(input: Input): Endpoint.Result[A] = input.route match {
      case ss +: rest => f(ss) match {
        case Some(a) =>
          EndpointResult.Matched(input.withRoute(rest), Rerunnable.const(Output.payload(a)))
        case _ =>
          EndpointResult.Skipped
      }
      case _ => EndpointResult.Skipped
    }

    def apply(n: String): Endpoint[A] = copy[A](name = n)

    override final def toString: String = s":$name"
  }

  private[finch] case class StringExtractor(name: String) extends Endpoint[String] {
    final def apply(input: Input): Endpoint.Result[String] = input.route match {
      case s +: rest => EndpointResult.Matched(input.withRoute(rest), Rerunnable.const(Output.payload(s)))
      case _ => EndpointResult.Skipped
    }

    final def apply(n: String): Endpoint[String] = copy(name = n)

    final override def toString: String = s":$name"
  }

  /**
   * An extractor that extracts a value of type `Seq[A]` from the tail of the route.
   */
  private[finch] case class TailExtractor[A](
      name: String,
      f: String => Option[A]) extends Endpoint[Seq[A]] {

    final def apply(input: Input): Endpoint.Result[Seq[A]] =
      EndpointResult.Matched(
        input.copy(route = Nil),
        Rerunnable.const(Output.payload(input.route.flatMap(f.andThen(_.toSeq))))
      )

    final def apply(n: String): Endpoint[Seq[A]] = copy[A](name = n)

    final override def toString: String = s":$name*"
  }

  private[this] def extractUUID(s: String): Option[UUID] =
    if (s.length != 36) None
    else try Some(UUID.fromString(s)) catch { case _: Exception => None }

  /**
   * A matching [[Endpoint]] that reads a string value from the current path segment.
   *
   * @note This is an experimental API and might be removed without any notice.
   */
  val path: Endpoint[String] = new Endpoint[String] {
    final def apply(input: Input): Endpoint.Result[String] = input.route match {
      case s +: rest =>
        EndpointResult.Matched(input.withRoute(rest), Rerunnable.const(Output.payload(s)))
      case _ =>
        EndpointResult.Skipped
    }

    final override def toString: String = ":path"
  }

  /**
   * A matching [[Endpoint]] that reads a value of type `A` (using the implicit
   * [[DecodePath]] instances defined for `A`) from the current path segment.
   */
  def path[A](implicit c: DecodePath[A]): Endpoint[A] = new Endpoint[A] {
    final def apply(input: Input): Endpoint.Result[A] = input.route match {
      case s +: rest => c(s) match {
        case Some(a) =>
          EndpointResult.Matched(input.withRoute(rest), Rerunnable.const(Output.payload(a)))
        case _ => EndpointResult.Skipped

      }
      case _ => EndpointResult.Skipped
    }

    final override def toString: String = ":path"
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
    final def apply(input: Input): Endpoint.Result[HNil] =
      EndpointResult.Matched(input.copy(route = Nil), Rs.OutputHNil)

    final override def toString: String = "*"
  }

  /**
   * An identity [[Endpoint]].
   */
  object / extends Endpoint[HNil] {
    final def apply(input: Input): Endpoint.Result[HNil] =
      EndpointResult.Matched(input, Rs.OutputHNil)

    final override def toString: String = ""
  }

  private[this] def method[A](m: Method)(r: Endpoint[A]): Endpoint[A] = new Endpoint[A] {
    final def apply(input: Input): Endpoint.Result[A] =
      if (input.request.method == m) r(input)
      else EndpointResult.Skipped

    final override def toString: String = s"${m.toString().toUpperCase} /${r.toString}"
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

  private[this] def requestUpload(upload: String)(req: Request): Option[FileUpload] =
    Try(req.multipart).getOrElse(None).flatMap(m => m.files.get(upload).flatMap(fs => fs.headOption))

  private[this] def option[A](item: items.RequestItem)(f: Request => A): Endpoint[A] =
    Endpoint.embed(item)(input =>
      EndpointResult.Matched(input, Rerunnable(Output.payload(f(input.request))))
    )

  private[this] def exists[A](item: items.RequestItem)(f: Request => Option[A]): Endpoint[A] =
    Endpoint.embed(item) { input =>
      f(input.request) match {
        case Some(a) => EndpointResult.Matched(input, Rerunnable(Output.payload(a)))
        case _ => EndpointResult.Skipped
      }
    }

  private[this] def matches[A]
    (item: items.RequestItem)
    (p: Request => Boolean)
    (f: Request => A): Endpoint[A] = Endpoint.embed(item)(input =>
      if (p(input.request))
        EndpointResult.Matched(input, Rerunnable(Output.payload(f(input.request))))
      else
        EndpointResult.Skipped
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
}
