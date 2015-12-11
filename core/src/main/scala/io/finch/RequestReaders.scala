package io.finch

import com.twitter.finagle.http.{Cookie, Request}
import com.twitter.finagle.http.exp.Multipart.FileUpload
import com.twitter.finagle.netty3.ChannelBufferBuf
import com.twitter.io.{Buf, Charsets}
import com.twitter.util.{Future, Try}

trait RequestReaders {

  import items._

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
  private[finch] def requestParam(param: String)(req: Request): Option[String] =
    req.params.get(param).orElse(req.multipart.flatMap(m => m.attributes.get(param).flatMap(_.headOption)))

  private[finch] def requestParams(params: String)(req: Request): Seq[String] =
    req.params.getAll(params).toList.flatMap(_.split(","))

  private[finch] def requestHeader(header: String)(req: Request): Option[String] =
    req.headerMap.get(header)

  private[finch] def requestCookie(cookie: String)(req: Request): Option[Cookie] =
    req.cookies.get(cookie)

  private[finch] def requestUpload(upload: String)(req: Request): Option[FileUpload] =
    Try(req.multipart).getOrElse(None).flatMap(m => m.files.get(upload).flatMap(fs => fs.headOption))

  // A convenient method for internal needs.
  private[finch] def rr[A](i: RequestItem)(f: Request => A): RequestReader[A] =
    RequestReader.embed[A](i)(r => Future.value(f(r)))

  /**
   * Creates a [[RequestReader]] that reads a required query-string param `name` from the request or raises an
   * [[Error.NotPresent]] exception when the param is missing; an [[Error.NotValid]] exception is the param is empty.
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
   * element) multi-value query-string param `name` from the request into a `Seq` or raises a [[Error.NotPresent]]
   * exception when the params are missing or empty.
   *
   * @param name the param to read
   *
   * @return a `Seq` that contains all the values of multi-value param
   */
  def paramsNonEmpty(name: String): RequestReader[Seq[String]] =
    rr(ParamItem(name))(requestParams(name)).embedFlatMap({
      case Nil => Future.exception(Error.NotPresent(ParamItem(name)))
      case unfiltered => Future.value(unfiltered.filter(_.nonEmpty))
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
   * Creates a [[RequestReader]] that reads a required HTTP header `name` from the request or raises an
   * [[Error.NotPresent]] exception when the header is missing.
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
  val binaryBodyOption: RequestReader[Option[Array[Byte]]] = rr(BodyItem)(req =>
    req.contentLength match {
      case Some(n) if n > 0 => Some(Buf.ByteArray.Shared.extract(req.content))
      case _ => None
    }
  )

  /**
   * A [[RequestReader]] that reads a required binary request body, interpreted as a `Array[Byte]`, or throws a
   * [[Error.NotPresent]] exception.
   */
  val binaryBody: RequestReader[Array[Byte]] = binaryBodyOption.failIfNone

  /**
   * A [[RequestReader]] that reads an optional request body, interpreted as a `String`, into an `Option`.
   */
  val bodyOption: RequestReader[Option[String]] = rr(BodyItem)(req =>
    req.contentLength match {
      case Some(n) if n > 0 =>
        val buffer = ChannelBufferBuf.Owned.extract(req.content)
        // Note: We usually have an array underneath the ChannelBuffer (at least on Netty 3).
        // This check is mostly about a safeguard.
        if (buffer.hasArray) Some(new String(buffer.array(), 0, buffer.readableBytes(), "UTF-8"))
        else Some(buffer.toString(Charsets.Utf8))
      case _ => None
    }
  )

  /**
   * A [[RequestReader]] that reads the required request body, interpreted as a `String`, or throws an
   * [[Error.NotPresent]] exception.
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
   * Creates a [[RequestReader]] that reads a required cookie from the request or raises an [[Error.NotPresent]]
   * exception when the cookie is missing.
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

  private[finch] val beEmpty: ValidationRule[String] = ValidationRule("be empty")(_.isEmpty)
}
