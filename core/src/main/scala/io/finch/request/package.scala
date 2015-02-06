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
import com.twitter.util.{Future, Return, Throw, Try}

import scala.reflect.ClassTag

package request {

  /**
   * Trait with low-priority implicits to avoid conflicts that would arise from adding
   * implicits that would work with any type in the same scope as implicits for concrete
   * types.
   *
   * Implicits defined in super-types have lower priority than those defined in a sub-type.
   * Therefore we define low-priority implicits here and mix this trait into the package
   * object.
   */
  trait LowPriorityImplicits {

    /**
     * Creates a ''DecodeMagnet'' from ''DecodeAnyRequest''.
     */
    implicit def magnetFromAnyDecode[A](implicit d: DecodeAnyRequest, tag: ClassTag[A]): DecodeMagnet[A] =
      new DecodeMagnet[A] {
        def apply(): DecodeRequest[A] = new DecodeRequest[A] {
          def apply(req: String): Try[A] = d(req)(tag)
        }
      }
    }

}

package object request extends LowPriorityImplicits {

  implicit val decodeInt: DecodeRequest[Int] = DecodeRequest { s => Try(s.toInt) }

  implicit val decodeLong: DecodeRequest[Long] = DecodeRequest { s => Try(s.toLong) }

  implicit val decodeFloat: DecodeRequest[Float] = DecodeRequest { s => Try(s.toFloat) }

  implicit val decodeDouble: DecodeRequest[Double] = DecodeRequest { s => Try(s.toDouble) }

  implicit val decodeBoolean: DecodeRequest[Boolean] = DecodeRequest { s => Try(s.toBoolean) }

  /**
   * A reusable validation rule that can be applied to any [[RequestReader]] with a matching type.
   */
  trait ValidationRule[A] { self =>

    /**
     * Text description of this validation rule.
     */
    def description: String

    /**
     * Applies the rule to the specified value.
     *
     * @return true if the predicate of this rule holds for the specified value
     */
    def apply(value: A): Boolean

    /**
     * Combines this rule with another rule such that the new
     * rule only validates if both the combined rules validate.
     *
     * @param that the rule to combine with this rule
     * @return a new rule that only validates if both the combined rules validate
     */
    def and(that: ValidationRule[A]): ValidationRule[A] =
      ValidationRule(s"${self.description} and ${that.description}") { value => self(value) && that(value) }

    /**
     * Combines this rule with another rule such that the new
     * rule validates if any one of the combined rules validates.
     *
     * @param that the rule to combine with this rule
     * @return a new rule that validates if any of the the combined rules validates
     */
    def or(that: ValidationRule[A]): ValidationRule[A] =
      ValidationRule(s"${self.description} or ${that.description}") { value => self(value) || that(value) }
  }

  /**
   * Allows the creation of reusable validation rules for [[RequestReader]]s.
   */
  object ValidationRule {

    /**
     * Creates a new reusable validation rule based on the specified predicate.
     *
     * @param desc text describing the rule being validated
     * @param p returns true if the data is valid
     *
     * @return a new reusable validation rule.
     */
    def apply[A](desc: String)(p: A => Boolean): ValidationRule[A] = new ValidationRule[A] {
      def description = desc
      def apply(value: A) = p(value)
    }
  }

  /**
   * Representations for the various request item types
   * that can be processed with ''RequestReaders''.
   */
  object items {
    sealed abstract class RequestItem(val kind: String, val nameOption:Option[String] = None) {
      val description = kind + nameOption.fold("")(" '"+_+"'")
    }
    case class ParamItem(name: String) extends RequestItem("param", Some(name))
    case class HeaderItem(name: String) extends RequestItem("header", Some(name))
    case class CookieItem(name: String) extends RequestItem("cookie", Some(name))
    case object BodyItem extends RequestItem("body")
    case object MultipleItems extends RequestItem("request")
  }

  import items._

  /**
   * A request reader (a Reader Monad) reads a ''Future'' of ''A'' from the ''HttpRequest''.
   *
   * @tparam A the result type
   */
  trait RequestReader[A] { self =>

    def item: RequestItem

    /**
     * Reads the data from given request ''req''.
     *
     * @tparam Req the request type
     * @param req the request to read
     */
    def apply[Req](req: Req)(implicit ev: Req => HttpRequest): Future[A]

    def flatMap[B](fn: A => RequestReader[B]) = new RequestReader[B] {
      val item = MultipleItems
      def apply[Req](req: Req)(implicit ev: Req => HttpRequest) = self(req) flatMap { fn(_)(req) }
    }

    def map[B](fn: A => B) = new RequestReader[B] {
      val item = self.item
      def apply[Req](req: Req)(implicit ev: Req => HttpRequest) = self(req) map fn
    }

    def embedFlatMap[B](fn: A => Future[B]) = new RequestReader[B] {
      val item = self.item
      def apply[Req](req: Req)(implicit ev: Req => HttpRequest) = self(req) flatMap fn
    }

    def ~[B](that: RequestReader[B]): RequestReader[A ~ B] = new RequestReader[A ~ B] {
      val item = MultipleItems
      def apply[Req] (req: Req)(implicit ev: Req => HttpRequest): Future[A ~ B] =
        Future.join(self(req)(ev).liftToTry, that(req)(ev).liftToTry) flatMap {
          case (Return(a), Return(b)) => new ~(a, b).toFuture
          case (Throw(a), Throw(b)) => collectExceptions(a, b).toFutureException
          case (Throw(e), _) => e.toFutureException
          case (_, Throw(e)) => e.toFutureException
        }

      def collectExceptions (a: Throwable, b: Throwable): RequestErrors = {
        def collect (e: Throwable): Seq[Throwable] = e match {
          case RequestErrors(errors) => errors
          case other => Seq(other)
        }

        RequestErrors(collect(a) ++ collect(b))
      }
    }

    def withFilter(p: A => Boolean) = self.should("not fail validation")(p)

    /**
     * Validates the result of this ''RequestReader'' using a predicate. The rule is used for error reporting.
     *
     * @param rule text describing the rule being validated
     * @param predicate returns true if the data is valid
     *
     * @return a ''RequestReader'' that will return the value of this reader if it is valid.
     *         Otherwise the future fails with a ''NotValid'' error.
     */
    def should(rule: String)(predicate: A => Boolean): RequestReader[A] = embedFlatMap { a =>
      if (predicate(a)) a.toFuture
      else NotValid(self.item, rule).toFutureException
    }

    /**
     * Validates the result of this ''RequestReader'' using a predicate. The rule is used for error reporting.
     *
     * @param rule text describing the rule being validated
     * @param predicate returns false if the data is valid
     *
     * @return a ''RequestReader'' that will return the value of this reader if it is valid.
     *         Otherwise the future fails with a ''NotValid'' error.
     */
    def shouldNot(rule: String)(predicate: A => Boolean): RequestReader[A] = should(s"not $rule.")(x => !predicate(x))

    /**
     * Validates the result of this ''RequestReader'' using a predefined rule. This method allows
     * for rules to be reused across multiple ''RequestReaders''.
     *
     * @param rule the predefined validation rule that will return true if the data is valid
     *
     * @return a ''RequestReader'' that will return the value of this reader if it is valid.
     *         Otherwise the future fails with a ''NotValid'' error.
     */
    def should(rule: ValidationRule[A]): RequestReader[A] = should(rule.description)(rule.apply)

    /**
     * Validates the result of this ''RequestReader'' using a predefined rule. This method allows
     * for rules to be reused across multiple ''RequestReaders''.
     *
     * @param rule the predefined validation rule that will return false if the data is valid
     *
     * @return a ''RequestReader'' that will return the value of this reader if it is valid.
     *         Otherwise the future fails with a ''NotValid'' error.
     */
    def shouldNot(rule: ValidationRule[A]): RequestReader[A] = shouldNot(rule.description)(rule.apply)
  }

  /**
   * Convenience methods for creating new reader instances.
   */
  object RequestReader {

    /**
     * Creates a new reader that always succeeds, producing the specified value.
     *
     * @param value the value the new reader should produce
     * @param item the request item (e.g. parameter, header) the value is associated with
     * @return a new reader that always succeeds, producing the specified value
     */
    def value[A](value: A, item: RequestItem = MultipleItems): RequestReader[A] = const(value.toFuture)

    /**
     * Creates a new reader that always fails, producing the specified exception.
     *
     * @param exc the exception the new reader should produce
     * @param item the request item (e.g. parameter, header) the value is associated with
     * @return a new reader that always fails, producing the specified exception
     */
    def exception[A](exc: Throwable, item: RequestItem = MultipleItems): RequestReader[A] = const(exc.toFutureException)

    /**
     * Creates a new reader that always produces the specified value.
     * It will succeed if the Future succeeds and fail if the Future fails.
     *
     * @param value the value the new reader should produce
     * @param item the request item (e.g. parameter, header) the value is associated with
     * @return a new reader that always produces the specified value
     */
    def const[A](value: Future[A], item: RequestItem = MultipleItems): RequestReader[A] = embed(item)(_ => value)

    /**
     * Creates a new reader that reads the result from the request.
     *
     * @param item the request item (e.g. parameter, header) the value is associated with
     * @param f the function to apply to the request
     * @return a new reader that reads the result from the request
     */
    def apply[A](item: RequestItem)(f: HttpRequest => A): RequestReader[A] = embed(item)(f(_).toFuture)

    private[this] def embed[A](reqItem: RequestItem)(f: HttpRequest => Future[A]): RequestReader[A] =
      new RequestReader[A] {
        val item = reqItem
        def apply[Req](req: Req)(implicit ev: Req => HttpRequest) = f(req)
      }
  }

  private[this] def notParsed[A](reader: RequestReader[_], tag: ClassTag[_]): PartialFunction[Throwable,Try[A]] = {
    case exc => Throw(NotParsed(reader.item, tag, exc))
  }

  /**
   * Implicit conversion that allows to call ''as[A]'' on any ''RequestReader[String]''
   * to perform a type conversion based on an implicit ''DecodeRequest[A]'' which must
   * be in scope.
   *
   * The resulting reader will fail when type conversion fails.
   */
  implicit class StringReaderOps(val reader: RequestReader[String]) extends AnyVal {
    def as[A](implicit magnet: DecodeMagnet[A], tag: ClassTag[A]): RequestReader[A] = reader embedFlatMap { value =>
      Future.const(magnet()(value).rescue(notParsed(reader, tag)))
    }
  }

  /**
   * Implicit conversion that allows to call ''as[A]'' on any ''RequestReader[Option[String]]''
   * to perform a type conversion based on an implicit ''DecodeRequest[A]'' which must
   * be in scope.
   *
   * The resulting reader will fail when the result is non-empty and type conversion fails.
   * It will succeed if the result is empty or type conversion succeeds.
   */
  implicit class StringOptionReaderOps(val reader: RequestReader[Option[String]]) extends AnyVal {
    def as[A](implicit magnet: DecodeMagnet[A], tag: ClassTag[A]): RequestReader[Option[A]] = reader embedFlatMap {
      case Some(value) => Future.const(magnet()(value).rescue(notParsed(reader, tag)) map (Some(_)))
      case None => Future.None
    }
  }

  /**
   * Implicit conversion that allows to call ''as[A]'' on any ''RequestReader[Seq[String]]''
   * to perform a type conversion based on an implicit ''DecodeRequest[A]'' which must
   * be in scope.
   *
   * The resulting reader will fail when the result is non-empty and type conversion fails
   * on one or more of the elements in the ''Seq''.
   * It will succeed if the result is empty or type conversion succeeds for all elements.
   */
  implicit class StringSeqReaderOps(val reader: RequestReader[Seq[String]]) {

    /* IMPLEMENTATION NOTE: This implicit class should extend AnyVal like all the other ones,
     * to avoid instance creation for each invocation of the extension method. However,
     * this let's us run into a compiler bug when we compile for Scala 2.10:
     * https://issues.scala-lang.org/browse/SI-8018. The bug is caused by the combination of
     * four things: 1) an implicit class, 2) extending AnyVal, 3) wrapping a class with type
     * parameters, 4) a partial function in the body. 2) is the only thing we can easily remove here,
     * otherwise we'd need to move the body of the method somewhere else. Once we drop support for
     * Scala 2.10, this class can safely extends AnyVal.
     */

    def as[A](implicit magnet: DecodeMagnet[A], tag: ClassTag[A]): RequestReader[Seq[A]] = reader embedFlatMap { items =>
      val converted = items map (magnet()(_))
      if (converted.forall(_.isReturn)) converted.map(_.get).toFuture
      else RequestErrors(converted collect { case Throw(e) => NotParsed(reader.item, tag, e) }).toFutureException
    }
  }

  /**
   * Implicit conversion that adds convenience methods to readers for optional values.
   */
  implicit class OptionReaderOps[A](val reader: RequestReader[Option[A]]) extends AnyVal {
    def failIfEmpty: RequestReader[A] = reader embedFlatMap {
      case Some(value) => value.toFuture
      case None => NotPresent(reader.item).toFutureException
    }
  }

  /**
   * Implicit conversion that allows the same validation rule to be used
   * for required and optional values. If the optional value is non-empty,
   * it gets validated (and validation may fail, producing an error), but
   * if it is empty, it is always treated as valid.
   *
   * @param rule The validation rule to adapt for optional values
   * @return A new validation rule that applies the specified rule to an optional value in case it is not empty.
   */
  implicit def toOptionalRule[A](rule: ValidationRule[A]): ValidationRule[Option[A]] = {
    ValidationRule(rule.description) {
      case Some(value) => rule(value)
      case None => true
    }
  }

  /**
   * A base exception of request reader.
   *
   * @param message the message
   */
  class RequestError(val message: String, cause: Throwable) extends Exception(message, cause) {
    def this(message: String) = this(message, null)
  }

  /**
   * An exception that collects multiple request reader errors.
   *
   * @param errors the errors collected from various request readers
   */
  case class RequestErrors(errors: Seq[Throwable])
    extends RequestError("One or more errors reading request: " + errors.map(_.getMessage).mkString("\n  ","\n  ",""))

  /**
   * An exception that indicates a required request item (header, param, cookie, body)
   * was missing in the request.
   *
   * @param item the missing request item
   */
  case class NotPresent(item: RequestItem) extends RequestError(s"Required ${item.description} not present in the request.")

  /**
   * An exception that indicates a broken validation rule on the request item.
   *
   * @param item the invalid request item
   * @param rule the rule description
   */
  case class NotValid(item: RequestItem, rule: String)
    extends RequestError(s"Validation failed: ${item.description} $rule.")

  /**
   * An exception that indicates that a request item could be parsed.
   *
   * @param item the invalid request item
   * @param targetType the type the item should be converted into
   * @param cause the cause of the parsing error
   */
  case class NotParsed(item: RequestItem, targetType: ClassTag[_], cause: Throwable)
    extends RequestError(s"${item.description} cannot be converted to ${targetType.runtimeClass.getSimpleName}: ${cause.getMessage}.")

  /**
   * A required string param.
   */
  object RequiredParam {

    /**
     * Creates a ''RequestReader'' that reads a required string ''param''
     * from the request or raises an exception when the param is missing or empty.
     *
     * @param param the param to read
     *
     * @return a param value
     */
    def apply(param: String): RequestReader[String] = OptionalParam(param).failIfEmpty.shouldNot("be empty")(_.trim.isEmpty)
  }

  /**
   * An optional string param.
   */
  object OptionalParam {

    /**
     * Creates a ''RequestReader'' that reads an optional string ''param''
     * from the request into an ''Option''.
     *
     * @param param the param to read
     *
     * @return an option that contains a param value or ''None'' if the param
     *         is empty or it doesn't correspond to the expected type
     */
    def apply(param: String): RequestReader[Option[String]] = RequestReader(ParamItem(param))(_.params.get(param))
  }

  /**
   * A helper function that encapsulates the logic necessary to read
   * a multi-value parameter.
   */
  private[this] object RequestParams {
    def apply(req: HttpRequest, param: String): Seq[String] = {
      req.params.getAll(param).toList.flatMap(_.split(","))
    }
  }

  /**
   * A required multi-value string param.
   */
  object RequiredParams {

    /**
     * Creates a ''RequestReader'' that reads a required multi-value string
     * ''param'' from the request into an ''List'' or raises an exception when the
     * param is missing or empty.
     *
     * @param param the param to read
     *
     * @return a ''List'' that contains all the values of multi-value param
     */
    def apply(param: String): RequestReader[Seq[String]] =
      (RequestReader(ParamItem(param))(RequestParams(_, param)) embedFlatMap {
        case Nil => NotPresent(ParamItem(param)).toFutureException
        case unfiltered => unfiltered.filter(_ != "").toFuture
      }).shouldNot("be empty")(_.isEmpty)
  }

  /**
   * An optional multi-value string param.
   */
  object OptionalParams {

    /**
     * Creates a ''RequestReader'' that reads an optional multi-value
     * string ''param'' from the request into an ''List''.
     *
     * @param param the param to read
     *
     * @return a ''List'' that contains all the values of multi-value param or
     *         en empty list ''Nil'' if the param is missing or empty.
     */
    def apply(param: String): RequestReader[Seq[String]] =
      RequestReader(ParamItem(param))(RequestParams(_, param).filter(_ != ""))
  }

  /**
   * A required header.
   */
  object RequiredHeader {

    /**
     * Creates a ''RequestReader'' that reads a required string ''header''
     * from the request or raises an exception when the header is missing.
     *
     * @param header the header to read
     *
     * @return a header
     */
    def apply(header: String): RequestReader[String] = OptionalHeader(header).failIfEmpty
  }

  /**
   * An optional header.
   */
  object OptionalHeader {

    /**
     * Creates a ''RequestReader'' that reads an optional string ''header''
     * from the request into an ''Option''.
     *
     * @param header the header to read
     *
     * @return an option that contains a header value or ''None'' if the header
     *         is not exist in the request
     */
    def apply(header: String): RequestReader[Option[String]] = RequestReader(HeaderItem(header))(_.headerMap.get(header))
  }

  /**
   * A helper function that encapsulates the logic necessary to turn the ''ChannelBuffer''
   * of ''req'' into an ''Array[Byte]''
   * @return The ''Array[Byte]'' representing the contents of the request body
   */
  private[this] object RequestBody {
    def apply(req: HttpRequest): Array[Byte] = {
      val buf = req.content
      val out = Array.ofDim[Byte](buf.length)
      buf.write(out, 0)
      out
    }
  }

  /**
   * A ''RequestReader'' that reads a binary request body, interpreted as a ''Array[Byte]'',
   * or throws a ''NotPresent'' exception.
   */
  object RequiredBinaryBody extends RequestReader[Array[Byte]] {
    val item = BodyItem
    def apply[Req](req: Req)(implicit ev: Req => HttpRequest): Future[Array[Byte]] = OptionalBinaryBody.failIfEmpty(req)
  }

  /**
   * A ''RequestReader'' that reads a binary request body, interpreted as a ''Array[Byte]'',
   * into an ''Option''.
   */
  object OptionalBinaryBody extends RequestReader[Option[Array[Byte]]] {
    val item = BodyItem
    def apply[Req](req: Req)(implicit ev: Req => HttpRequest): Future[Option[Array[Byte]]] =
      req.contentLength match {
        case Some(length) if length > 0 => Some(RequestBody(req)).toFuture
        case _ => Future.None
      }
  }

  /**
   * A ''RequestReader'' that reads the request body, interpreted as a ''String'',
   * or throws a ''NotPresent'' exception.
   */
  object RequiredBody extends RequestReader[String] {
    val item = BodyItem
    def apply[Req](req: Req)(implicit ev: Req => HttpRequest): Future[String] = OptionalBody.failIfEmpty(req)
  }

  /**
   * A ''RequestReader'' that reads the request body, interpreted as a ''String'',
   * into an ''Option''.
   */
  object OptionalBody extends RequestReader[Option[String]] {
    val item = BodyItem
    def apply[Req](req: Req)(implicit ev: Req => HttpRequest): Future[Option[String]] = for {
      b <- OptionalBinaryBody(req)
    } yield b map (new String(_, "UTF-8"))
  }

  /**
   * An optional cookie reader.
   */
  object OptionalCookie {
    /**
     * Creates a ''RequestReader'' that reads an optional cookie from the request
     *
     * @param cookie the name of the cookie to read
     *
     * @return An option that contains a cookie or None if the cookie does not exist on the request.
     */
    def apply(cookie: String): RequestReader[Option[Cookie]] = RequestReader(CookieItem(cookie))(_.cookies.get(cookie))
  }

  /**
   * A Required Cookie
   */
  object RequiredCookie {
    /**
     * Creates a ''RequestReader'' that reads a required cookie from the request
     * or raises an exception when the cookie is missing.
     *
     * @param cookieName the name of the cookie to read
     *
     * @return the cookie
     */
    def apply(cookieName: String): RequestReader[Cookie] = OptionalCookie(cookieName).failIfEmpty
  }

  /**
   * An abstraction that is responsible for decoding the request of type ''A''.
   */
  trait DecodeRequest[+A] {
    def apply(req: String): Try[A]
  }

  /**
   * Convenience method for creating new DecodeRequest instances.
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
   * A magnet that wraps a ''DecodeRequest''.
   */
  trait DecodeMagnet[A] {
    def apply(): DecodeRequest[A]
  }

  /**
   * Creates a ''DecodeMagnet'' from ''DecodeRequest''.
   */
  implicit def magnetFromDecode[A](implicit d: DecodeRequest[A]): DecodeMagnet[A] =
    new DecodeMagnet[A] {
      def apply(): DecodeRequest[A] = d
    }

  /** A wrapper for two result values.
   */
  case class ~[+A, +B](_1: A, _2: B)
}
