/*
 * Copyright 2014, by Vladimir Kostyukov and Contributors.
 *
 * This file is a part of a Finch library that may be found at
 *
 *      https://github.com/finagle/finch
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributor(s):
 * Ben Whitehead
 * Ryan Plessner
 * Pedro Viegas
 * Jens Halm
 */

package io.finch

import com.twitter.finagle.httpx.Cookie
import com.twitter.io.Buf
import com.twitter.util.{Future, Throw, Try}

import scala.reflect.ClassTag

package request {

  /**
   * Trait with low-priority implicits to avoid conflicts that would arise from adding implicits that would work with
   * any type in the same scope as implicits for concrete types.
   *
   * Implicits defined in super-types have lower priority than those defined in a sub-type. Therefore we define low-
   * priority implicits here and mix this trait into the package object.
   */
  trait LowPriorityImplicits {

    /**
     * Creates a [[io.finch.request.DecodeMagnet DecodeMagnet]] from
     * [[io.finch.request.DecodeAnyRequest DecodeAnyRequest]].
     */
    implicit def magnetFromAnyDecode[A](implicit d: DecodeAnyRequest, tag: ClassTag[A]): DecodeMagnet[A] =
      new DecodeMagnet[A] {
        def apply(): DecodeRequest[A] = new DecodeRequest[A] {
          def apply(req: String): Try[A] = d(req)(tag)
        }
      }
  }
}

/**
 * This package introduces types and functions that enable _request processing_ in Finch. The [[io.finch.request]]
 * primitives allow both to _read_ the various request items (''query string param'', ''header'' and ''cookie'') using
 * the [[io.finch.request.RequestReader RequestReader]] and _validate_ them using the
 * [[io.finch.request.ValidationRule ValidationRule]].
 *
 * The cornerstone abstraction of this package is a `RequestReader`, which is responsible for reading any amount of data
 * from the HTTP request. `RequestReader`s might be composed with each other using either monadic API (`flatMap` method)
 * or applicative API (`~` method). Regardless the API used for `RequestReader`s composition, the main idea behind it is
 * to use primitive readers (i.e., `RequiredParam`, `OptionalParam`) in order to _compose_ them together and _map_ to
 * the application domain data.
 *
 * {{{
 *   case class Complex(r: Double, i: Double)
 *   val complex: RequestReader[Complex] =
 *     RequiredParam("real").as[Double] ~
 *     OptionalParam("imaginary").as[Double] map {
 *       case r ~ i => Complex(r, i.getOrElse(0.0))
 *     }
 * }}}
 *
 * A `ValidationRule` enables a reusable way of defining a validation rules in the application domain. It might be
 * composed with `RequestReader`s using either `should` or `shouldNot` methods and with other `ValidationRule`s using
 * logical methods `and` and `or`.
 *
 * {{{
 *   case class User(name: String, age: Int)
 *   val user: RequestReader[User] =
 *     RequiredParam("name") should beLongerThan(3) ~
 *     RequiredParam("age").as[Int] should (beGreaterThan(0) and beLessThan(120)) map {
 *       case name ~ age => User(name, age)
 *     }
 * }}}
 */
package object request extends LowPriorityImplicits {

  /**
   * A [[io.finch.request.DecodeRequest DecodeRequest]] instance for `Int`.
   */
  implicit val decodeInt: DecodeRequest[Int] = DecodeRequest { s => Try(s.toInt) }

  /**
   * A [[io.finch.request.DecodeRequest DecodeRequest]] instance for `Long`.
   */
  implicit val decodeLong: DecodeRequest[Long] = DecodeRequest { s => Try(s.toLong) }

  /**
   * A [[io.finch.request.DecodeRequest DecodeRequest]] instance for `Float`.
   */
  implicit val decodeFloat: DecodeRequest[Float] = DecodeRequest { s => Try(s.toFloat) }

  /**
   * A [[DecodeRequest]] instance for `Double`.
   */
  implicit val decodeDouble: DecodeRequest[Double] = DecodeRequest { s => Try(s.toDouble) }

  /**
   * A [[io.finch.request.DecodeRequest DecodeRequest]] instance for `Boolean`.
   */
  implicit val decodeBoolean: DecodeRequest[Boolean] = DecodeRequest { s => Try(s.toBoolean) }

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

  import items._

  private[this] def notParsed[A](reader: RequestReader[_], tag: ClassTag[_]): PartialFunction[Throwable,Try[A]] = {
    case exc => Throw(NotParsed(reader.item, tag, exc))
  }

  /**
   * Implicit conversion that allows to call `as[A]` on any `RequestReader[String]` to perform a type conversion based
   * on an implicit `DecodeRequest[A]` which must be in scope.
   *
   * The resulting reader will fail when type conversion fails.
   */
  implicit class StringReaderOps(val reader: RequestReader[String]) extends AnyVal {
    def as[A](implicit magnet: DecodeMagnet[A], tag: ClassTag[A]): RequestReader[A] = reader embedFlatMap { value =>
      Future.const(magnet()(value).rescue(notParsed(reader, tag)))
    }
  }

  /**
   * Implicit conversion that allows to call `as[A]` on any `RequestReader[Option[String]]` to perform a type conversion
   * based on an implicit `DecodeRequest[A]` which must be in scope.
   *
   * The resulting reader will fail when the result is non-empty and type conversion fails. It will succeed if the
   * result is empty or type conversion succeeds.
   */
  implicit class StringOptionReaderOps(val reader: RequestReader[Option[String]]) extends AnyVal {
    def as[A](implicit magnet: DecodeMagnet[A], tag: ClassTag[A]): RequestReader[Option[A]] = reader embedFlatMap {
      case Some(value) => Future.const(magnet()(value).rescue(notParsed(reader, tag)) map (Some(_)))
      case None => Future.None
    }

    private[request] def noneIfEmpty: RequestReader[Option[String]] = reader map {
      case Some(value) if value.isEmpty => None
      case other => other
    }
  }

  /**
   * Implicit conversion that allows to call `as[A]` on any `RequestReader[Seq[String]]` to perform a type conversion
   * based on an implicit ''DecodeRequest[A]'' which must be in scope.
   *
   * The resulting reader will fail when the result is non-empty and type conversion fails on one or more of the
   * elements in the `Seq`. It will succeed if the result is empty or type conversion succeeds for all elements.
   */
  implicit class StringSeqReaderOps(val reader: RequestReader[Seq[String]]) {

    /* IMPLEMENTATION NOTE: This implicit class should extend AnyVal like all the other ones, to avoid instance creation
     * for each invocation of the extension method. However, this let's us run into a compiler bug when we compile for
     * Scala 2.10: https://issues.scala-lang.org/browse/SI-8018. The bug is caused by the combination of four things:
     * 1) an implicit class, 2) extending AnyVal, 3) wrapping a class with type parameters, 4) a partial function in the
     * body. 2) is the only thing we can easily remove here, otherwise we'd need to move the body of the method
     * somewhere else. Once we drop support for Scala 2.10, this class can safely extends AnyVal.
     */

    def as[A](implicit magnet: DecodeMagnet[A], tag: ClassTag[A]): RequestReader[Seq[A]] =
      reader embedFlatMap { items =>
        val converted = items map (magnet()(_))
        if (converted.forall(_.isReturn)) converted.map(_.get).toFuture
        else RequestErrors(converted collect { case Throw(e) => NotParsed(reader.item, tag, e) }).toFutureException
      }
  }

  /**
   * Implicit conversion that adds convenience methods to readers for optional values.
   */
  implicit class OptionReaderOps[A](val reader: RequestReader[Option[A]]) extends AnyVal {
    // TODO: better name. See #187.
    private[request] def failIfNone: RequestReader[A] = reader embedFlatMap {
      case Some(value) => value.toFuture
      case None => NotPresent(reader.item).toFutureException
    }
  }

  /**
   * Implicit conversion that allows the same [[io.finch.request.ValidationRule ValudationRule]] to be used for required
   * and optional values. If the optional value is non-empty, it gets validated (and validation may fail, producing an
   * error), but if it is empty, it is always treated as valid.
   *
   * @param rule the validation rule to adapt for optional values
   * @return a new validation rule that applies the specified rule to an optional value in case it is not empty
   */
  implicit def toOptionalRule[A](rule: ValidationRule[A]): ValidationRule[Option[A]] = {
    ValidationRule(rule.description) {
      case Some(value) => rule(value)
      case None => true
    }
  }

  /**
   * Implicit conversion that allows the same inline rules to be used for required and optional values. If the optional
   * value is non-empty, it gets validated (and validation may fail, producing error), but if it is empty, it is always
   * treated as valid.
   *
   * In order to help the compiler determine the case when inline rule should be converted, the type of the validated
   * value should be specified explicitly.
   *
   * {{{
   *   OptionalIntParam("foo").should("be greater than 50") { i: Int => i > 50 }
   * }}}
   *
   * @param fn the underlying function to convert
   */
  implicit def toOptionalInlineRule[A](fn: A => Boolean): Option[A] => Boolean = {
    case Some(value) => fn(value)
    case None => true
  }

  // Helper functions.
  private[request] def requestParam(param: String)(req: HttpRequest): Option[String] =
    req.params.get(param)

  private[request] def requestParams(params: String)(req: HttpRequest): Seq[String] =
    req.params.getAll(params).toList.flatMap(_.split(","))

  private[request] def requestHeader(header: String)(req: HttpRequest): Option[String] =
    req.headerMap.get(header)

  private[request] def requestBody(req: HttpRequest): Array[Byte] =
    Buf.ByteArray.Shared.extract(req.content)

  private[request] def requestCookie(cookie: String)(req: HttpRequest): Option[Cookie] =
    req.cookies.get(cookie)

  /**
   * A required string param.
   */
  object RequiredParam {

    /**
     * Creates a [[io.finch.request.RequestReader RequestReader]] that reads a required string ''param'' from the
     * request or raises a [[io.finch.request.NotPresent NotPresent]] exception when the param is missing or empty.
     *
     * @param param the param to read
     *
     * @return a param value
     */
    def apply(param: String): RequestReader[String] =
      RequestReader(ParamItem(param))(requestParam(param)).failIfNone shouldNot beEmpty
  }

  /**
   * An optional string param.
   */
  object OptionalParam {

    /**
     * Creates a [[io.finch.request.RequestReader RequestReader]] that reads an optional string ''param'' from the
     * request into an `Option`.
     *
     * @param param the param to read
     *
     * @return an `Option` that contains a param value or `None` if the param is empty
     */
    def apply(param: String): RequestReader[Option[String]] =
      RequestReader(ParamItem(param))(requestParam(param)).noneIfEmpty
  }

  /**
   * A required multi-value string param.
   */
  object RequiredParams {

    /**
     * Creates a [[io.finch.request.RequestReader RequestReader]] that reads a required multi-value string ''param''
     * from the request into a `Seq` or raises a [[io.finch.request.NotPresent NotPresent]] exception when the param is
     * missing or empty.
     *
     * @param param the param to read
     *
     * @return a `Seq` that contains all the values of multi-value param
     */
    def apply(param: String): RequestReader[Seq[String]] =
      (RequestReader(ParamItem(param))(requestParams(param)) embedFlatMap {
        case Nil => NotPresent(ParamItem(param)).toFutureException
        case unfiltered => unfiltered.filter(_.nonEmpty).toFuture
      }).shouldNot("be empty")(_.isEmpty)
  }

  /**
   * An optional multi-value string param.
   */
  object OptionalParams {

    /**
     * Creates a [[io.finch.request.RequestReader RequestReader]] that reads an optional multi-value string ''param''
     * from the request into a `Seq`.
     *
     * @param param the param to read
     *
     * @return a `Seq` that contains all the values of multi-value param or an empty seq `Nil` if the param is missing
     *         or empty.
     */
    def apply(param: String): RequestReader[Seq[String]] =
      RequestReader(ParamItem(param))(requestParams(param)(_).filter(_.nonEmpty))
  }

  /**
   * A required header.
   */
  object RequiredHeader {

    /**
     * Creates a [[io.finch.request.RequestReader RequestReader]] that reads a required string ''header'' from the
     * request or raises a [[io.finch.request.NotPresent NotPresent]] exception when the header is missing.
     *
     * @param header the header to read
     *
     * @return a header
     */
    def apply(header: String): RequestReader[String] =
      RequestReader(HeaderItem(header))(requestHeader(header)).failIfNone shouldNot beEmpty
  }

  /**
   * An optional header.
   */
  object OptionalHeader {

    /**
     * Creates a [[io.finch.request.RequestReader RequestReader]] that reads an optional string ''header'' from the
     * request into an `Option`.
     *
     * @param header the header to read
     *
     * @return an `Option` that contains a header value or `None` if the header is not present in the request
     */
    def apply(header: String): RequestReader[Option[String]] =
      RequestReader(HeaderItem(header))(requestHeader(header)).noneIfEmpty
  }

  /**
   * A [[io.finch.request.RequestReader RequestReader]] that reads a binary request ''body'', interpreted as a
   * `Array[Byte]`, or throws a [[io.finch.request.NotPresent NotPresent]] exception.
   */
  object RequiredBinaryBody extends RequestReader[Array[Byte]] {
    val item = BodyItem
    def apply[Req](req: Req)(implicit ev: Req => HttpRequest): Future[Array[Byte]] = OptionalBinaryBody.failIfNone(req)
  }

  /**
   * A [[io.finch.request.RequestReader RequestReader]] that reads a binary request ''body'', interpreted as a
   * `Array[Byte]`, into an `Option`.
   */
  object OptionalBinaryBody extends RequestReader[Option[Array[Byte]]] {
    val item = BodyItem
    def apply[Req](req: Req)(implicit ev: Req => HttpRequest): Future[Option[Array[Byte]]] =
      req.contentLength match {
        case Some(length) if length > 0 => Some(requestBody(req)).toFuture
        case _ => Future.None
      }
  }

  /**
   * A [[io.finch.request.RequestReader RequestReader]] that reads the request body, interpreted as a `String`, or
   * throws a [[io.finch.request.NotPresent NotPresent]] exception.
   */
  object RequiredBody extends RequestReader[String] {
    val item = BodyItem
    def apply[Req](req: Req)(implicit ev: Req => HttpRequest): Future[String] = OptionalBody.failIfNone(req)
  }

  /**
   * A [[io.finch.request.RequestReader RequestReader]] that reads the request body, interpreted as a `String`, into an
   * `Option`.
   */
  object OptionalBody extends RequestReader[Option[String]] {
    val item = BodyItem
    def apply[Req](req: Req)(implicit ev: Req => HttpRequest): Future[Option[String]] = for {
      b <- OptionalBinaryBody(req)
    } yield b map (new String(_, "UTF-8"))
  }

  /**
   * An optional cookie.
   */
  object OptionalCookie {
    /**
     * Creates a [[io.finch.request.RequestReader RequestReader]] that reads an optional cookie from the request.
     *
     * @param cookie the name of the cookie to read
     *
     * @return an `Option` that contains a cookie or None if the cookie does not exist on the request.
     */
    def apply(cookie: String): RequestReader[Option[Cookie]] = RequestReader(CookieItem(cookie))(requestCookie(cookie))
  }

  /**
   * A required cookie.
   */
  object RequiredCookie {

    /**
     * Creates a [[RequestReader]] that reads a required cookie from the request or raises a [[NotPresent]] exception
     * when the cookie is missing.
     *
     * @param cookieName the name of the cookie to read
     *
     * @return the cookie
     */
    def apply(cookieName: String): RequestReader[Cookie] = OptionalCookie(cookieName).failIfNone
  }

  /**
   * An abstraction that is responsible for decoding the request of type `A`.
   */
  trait DecodeRequest[+A] {
    def apply(req: String): Try[A]
  }

  /**
   * Convenience method for creating new [[io.finch.request.DecodeRequest DecodeRequest]] instances.
   */
  object DecodeRequest {
    def apply[A](f: String => Try[A]): DecodeRequest[A] = new DecodeRequest[A] {
      def apply(value: String): Try[A] = f(value)
    }
  }

  /**
   * An abstraction that is responsible for decoding the request of general type.
   */
  trait DecodeAnyRequest {
    def apply[A: ClassTag](req: String): Try[A]
  }

  /**
   * A magnet that wraps a [[io.finch.request.DecodeRequest DecodeRequest]].
   */
  trait DecodeMagnet[A] {
    def apply(): DecodeRequest[A]
  }

  /**
   * Creates a [[io.finch.request.DecodeMagnet DecodeMagnet]] from [[io.finch.request.DecodeRequest DecodeRequest]].
   */
  implicit def magnetFromDecode[A](implicit d: DecodeRequest[A]): DecodeMagnet[A] =
    new DecodeMagnet[A] {
      def apply(): DecodeRequest[A] = d
    }

  /**
   * A wrapper for two result values.
   */
  case class ~[+A, +B](_1: A, _2: B)

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
