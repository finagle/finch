package io.finch

import com.twitter.finagle.httpx.{Cookie, Method, Request}
import com.twitter.io.Buf
import org.jboss.netty.handler.codec.http.multipart.{Attribute, HttpPostRequestDecoder}

import scala.collection.JavaConverters._

/**
 * This package introduces types and functions that enable _request processing_ in Finch. The [[io.finch.request]]
 * primitives allow both to _read_ the various request items (''query string param'', ''header'' and ''cookie'') using
 * the [[io.finch.request.RequestReader RequestReader]] and _validate_ them using the
 * [[io.finch.request.ValidationRule ValidationRule]].
 *
 * The cornerstone abstraction of this package is a `RequestReader`, which is responsible for reading any amount of data
 * from the HTTP request. `RequestReader`s might be composed with each other using either monadic API (`flatMap` method)
 * or applicative API (`::` method). Regardless the API used for `RequestReader`s composition, the main idea behind it
 * is to use primitive readers (i.e., `param`, `paramOption`) in order to _compose_ them together and _map_ to
 * the application domain data.
 *
 * {{{
 *   case class Complex(r: Double, i: Double)
 *   val complex: RequestReader[Complex] = (
 *     param("real").as[Double] ::
 *     paramOption("imaginary").as[Double].withDefault(0.0)
 *   ).as[Complex]
 * }}}
 *
 * A `ValidationRule` enables a reusable way of defining a validation rules in the application domain. It might be
 * composed with `RequestReader`s using either `should` or `shouldNot` methods and with other `ValidationRule`s using
 * logical methods `and` and `or`.
 *
 * {{{
 *   case class User(name: String, age: Int)
 *   val user: RequestReader[User] = (
 *     param("name").should(beLongerThan(3)) ::
 *     param("age").as[Int].should(beGreaterThan(0) and beLessThan(120))
 *   ).as[User]
 * }}}
 */
package object request {

  /**
    * A type alias for a [[org.jboss.netty.handler.codec.http.multipart.FileUpload]]
    * to prevent imports.
    */
  type FileUpload = org.jboss.netty.handler.codec.http.multipart.FileUpload

  type %>[A, B] = View[A, B]

  /**
   * A [[PRequestReader]] with request type fixed to [[com.twitter.finagle.httpx.Request]].
   */
  type RequestReader[A] = PRequestReader[Request, A]

  /**
   * Representations for the various types that can be processed with [[io.finch.request.RequestReader RequestReader]]s.
   */
  object items {
    sealed abstract class RequestItem(val kind: String, val nameOption:Option[String] = None) {
      val description = kind + nameOption.fold("")(" '" + _ + "'")
    }
    case class ParamItem(name: String) extends RequestItem("param", Some(name))
    case class HeaderItem(name: String) extends RequestItem("header", Some(name))
    case class CookieItem(name: String) extends RequestItem("cookie", Some(name))
    case object BodyItem extends RequestItem("body")
    case object MultipleItems extends RequestItem("request")
  }

  import io.finch.request.items._

  /**
   * Implicit conversion that allows the same inline rules to be used for required and optional values. If the optional
   * value is non-empty, it gets validated (and validation may fail, producing error), but if it is empty, it is always
   * treated as valid.
   *
   * In order to help the compiler determine the case when inline rule should be converted, the type of the validated
   * value should be specified explicitly.
   *
   * {{{
   *   paramOption("foo").should("be greater than 50") { i: Int => i > 50 }
   * }}}
   *
   * @param fn the underlying function to convert
   */
  implicit def toOptionalInlineRule[A](fn: A => Boolean): Option[A] => Boolean = {
    case Some(value) => fn(value)
    case None => true
  }

  // Helper functions.
  private[request] def requestParam(param: String)(req: Request): Option[String] =
    req.params.get(param) orElse {
      import com.twitter.finagle.httpx.netty.Bijections._
      val nettyReq = from(req)
      if (req.method == Method.Post && HttpPostRequestDecoder.isMultipart(nettyReq)) {
        val decoder = new HttpPostRequestDecoder(from(req))
        decoder.getBodyHttpDatas.asScala.find(_.getName == param).flatMap {
          case attr: Attribute => Some(attr.getValue)
          case _ => None
        }
      } else None
    }

  private[request] def requestParams(params: String)(req: Request): Seq[String] =
    req.params.getAll(params).toList.flatMap(_.split(","))

  private[request] def requestHeader(header: String)(req: Request): Option[String] =
    req.headerMap.get(header)

  private[request] def requestBody(req: Request): Array[Byte] =
    Buf.ByteArray.Shared.extract(req.content)

  private[request] def requestCookie(cookie: String)(req: Request): Option[Cookie] =
    req.cookies.get(cookie)

  private[request] def requestUpload(upload: String)(req: Request): Option[FileUpload] = {
    import com.twitter.finagle.httpx.netty.Bijections._
    val decoder = new HttpPostRequestDecoder(from(req))
    decoder.getBodyHttpDatas.asScala.find(_.getName == upload).flatMap {
      case file: FileUpload => Some(file)
      case _ => None
    }
  }

  // A convenient method for internal needs.
  private[request] def rr[A](i: RequestItem)(f: Request => A): RequestReader[A] =
    RequestReader.embed[Request, A](i)(f(_).toFuture)

  /**
   * Creates a [[RequestReader]] that reads a required query-string param `name` from the request or raises a
   * [[NotPresent]] exception when the param is missing; a [[NotValid]] exception is the param is empty.
   *
   * @param name the param name to read
   *
   * @return a param value
   */
  def param(name: String): RequestReader[String] =
    rr(ParamItem(name))(requestParam(name)).failIfNone.shouldNot(beEmpty)

  /**
   * Creates a [[RequestReader]] that reads an optional query-string param `name` from the request into an `Option`.
   *
   * @param name the param to read
   *
   * @return an `Option` that contains a param value or `None` if the param is empty
   */
  def paramOption(name: String): RequestReader[Option[String]] =
    rr(ParamItem(name))(requestParam(name)).noneIfEmpty

  /**
   * Creates a [[RequestReader]] that reads a required (in a meaning that a resulting `Seq` will have at least one
   * element) multi-value query-string param `name` from the request into a `Seq` or raises a [[NotPresent]] exception
   * when the params are missing or empty.
   *
   * @param name the param to read
   *
   * @return a `Seq` that contains all the values of multi-value param
   */
  def paramsNonEmpty(name: String): RequestReader[Seq[String]] =
    rr(ParamItem(name))(requestParams(name)).embedFlatMap({
      case Nil => NotPresent(ParamItem(name)).toFutureException[Seq[String]]
      case unfiltered => unfiltered.filter(_.nonEmpty).toFuture
    }).shouldNot("be empty")(_.isEmpty)

  /**
   * Creates a [[RequestReader]] that reads an optional (in a meaning that a resulting `Seq` may be empty) multi-value
   * query-string param `name` from the request into a `Seq`.
   *
   * @param name the param to read
   *
   * @return a `Seq` that contains all the values of multi-value param or an empty seq `Nil` if the params are missing
   *         or empty.
   */
  def params(name: String): RequestReader[Seq[String]] =
    rr(ParamItem(name))(requestParams(name)(_).filter(_.nonEmpty))

  /**
   * Creates a [[RequestReader]] that reads a required HTTP header `name` from the request or raises a [[NotPresent]]
   * exception when the header is missing.
   *
   * @param name the header to read
   *
   * @return a header
   */
  def header(name: String): RequestReader[String] =
    rr(HeaderItem(name))(requestHeader(name)).failIfNone.shouldNot(beEmpty)

  /**
   * Creates a [[RequestReader]] that reads an optional HTTP header `name` from the request into an `Option`.
   *
   * @param name the header to read
   *
   * @return an `Option` that contains a header value or `None` if the header is not present in the request
   */
  def headerOption(name: String): RequestReader[Option[String]] =
    rr(HeaderItem(name))(requestHeader(name)).noneIfEmpty

  /**
   * A [[RequestReader]] that reads a binary request body, interpreted as a `Array[Byte]`, into an `Option`.
   */
  val binaryBodyOption: RequestReader[Option[Array[Byte]]] = rr(BodyItem) { req =>
    req.contentLength.flatMap(length =>
      if (length > 0) Some(requestBody(req)) else None
    )
  }

  /**
   * A [[RequestReader]] that reads a required binary request body, interpreted as a `Array[Byte]`, or throws a
   * [[NotPresent]] exception.
   */
  val binaryBody: RequestReader[Array[Byte]] = binaryBodyOption.failIfNone

  /**
   * A [[RequestReader]] that reads an optional request body, interpreted as a `String`, into an `Option`.
   */
  val bodyOption: RequestReader[Option[String]] = binaryBodyOption.map(_.map(new String(_, "UTF-8")))

  /**
   * A [[RequestReader]] that reads the required request body, interpreted as a `String`, or throws a [[NotPresent]]
   * exception.
   */
  val body: RequestReader[String] = bodyOption.failIfNone

  /**
   * Creates a [[RequestReader]] that reads an optional HTTP cookie from the request into an `Option`.
   *
   * @param name the name of the cookie to read
   *
   * @return an `Option` that contains a cookie or None if the cookie does not exist on the request.
   */
  def cookieOption(name: String): RequestReader[Option[Cookie]] = rr(CookieItem(name))(requestCookie(name))

  /**
   * Creates a [[RequestReader]] that reads a required cookie from the request or raises a [[NotPresent]] exception
   * when the cookie is missing.
   *
   * @param name the name of the cookie to read
   *
   * @return the cookie
   */
  def cookie(name: String): RequestReader[Cookie] = cookieOption(name).failIfNone

  /**
   * Creates a [[RequestReader]] that reads an optional file upload from a multipart/form-data request into an `Option`.
   *
   * @param name the name of the parameter to read
   * @return an `Option` that contains the file or `None` is the parameter does not exist on the request.
   */
  def fileUploadOption(name: String): RequestReader[Option[FileUpload]] = rr(ParamItem(name))(requestUpload(name))

  /**
   * Creates a [[RequestReader]] that reads a required file upload from a multipart/form-data request.
   *
   * @param name the name of the parameter to read
   * @return the file
   */
  def fileUpload(name: String): RequestReader[FileUpload] = fileUploadOption(name).failIfNone

  private[request] val beEmpty: ValidationRule[String] = ValidationRule("be empty")(_.isEmpty)

  /**
   * A [[io.finch.request.ValidationRule ValidationRule]] that makes sure the numeric value is greater than given `n`.
   */
  def beGreaterThan[A](n: A)(implicit ev: Numeric[A]): ValidationRule[A] =
    ValidationRule(s"be greater than $n")(ev.gt(_, n))

  /**
   * A [[[io.finch.request.ValidationRule ValidationRule]] that makes sure the numeric value is less than given `n`.
   */
  def beLessThan[A](n: A)(implicit ev: Numeric[A]): ValidationRule[A] =
    ValidationRule(s"be less than $n")(ev.lt(_, n))

  /**
   * A [[[io.finch.request.ValidationRule ValidationRule]] that makes sure the string value is longer than `n` symbols.
   */
  def beLongerThan(n: Int): ValidationRule[String] =
    ValidationRule(s"be longer than $n symbols")(_.length > n)

  /**
   * A [[[io.finch.request.ValidationRule ValidationRule]] that makes sure the string value is shorter than `n` symbols.
   */
  def beShorterThan(n: Int): ValidationRule[String] =
    ValidationRule(s"be shorter than $n symbols")(_.length < n)
}
