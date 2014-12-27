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
 */

package io.finch

import com.twitter.finagle.httpx.Cookie
import com.twitter.util.Future
import io.finch.json.DecodeJson

package object request {

  /**
   * A request reader (a Reader Monad) reads a ''Future'' of ''A'' from the ''HttpRequest''.
   *
   * @tparam A the result type
   */
  trait RequestReader[A] { self =>

    /**
     * Reads the data from given ''req''.
     *
     * @param req the request to read
     */
    def apply(req: HttpRequest): Future[A]

    def flatMap[B](fn: A => RequestReader[B]) = new RequestReader[B] {
      def apply(req: HttpRequest) = self(req) flatMap { fn(_)(req) }
    }

    def map[B](fn: A => B) = new RequestReader[B] {
      def apply(req: HttpRequest) = self(req) map fn
    }

    // A workaround for https://issues.scala-lang.org/browse/SI-1336
    def withFilter(p: A => Boolean) = self
  }

  /**
   * A base exception of request reader.
   *
   * @param message the message
   */
  class RequestReaderError(val message: String) extends Exception(message)

  /**
   * An exception that indicates missed parameter in the request.
   *
   * @param param the missed parameter name
   */
  case class ParamNotFound(param: String) extends RequestReaderError(s"Param '$param' not found in the request.")

  /**
   * An exception that indicates a broken validation rule on the param.
   *
   * @param param the param name
   * @param rule the rule description
   */
  case class ValidationFailed(param: String, rule: String)
    extends RequestReaderError(s"Request validation failed: param '$param' $rule.")

  /**
   * An exception that indicates missed header in the request.
   *
   * @param header the missed header name
   */
  case class HeaderNotFound(header: String) extends RequestReaderError(s"Header '$header' not found in the request.")

  /**
   * An exception that indicates a missing cookie in the request.
   *
   * @param cookie the missing cookie's name
   */
  case class CookieNotFound(cookie: String) extends RequestReaderError(s"Cookie '$cookie' not found in the request.")

  /**
   * An exception that indicated a missing body in the request.
   */
  object BodyNotFound extends RequestReaderError("Body not found in the request.")

  /**
   * An exception that indicates an error in JSON format.
   */
  object JsonNotParsed extends RequestReaderError("A JSON serialized in a request body can not be parsed.")

  /**
   * An empty ''RequestReader''.
   */
  object EmptyReader extends RequestReader[Nothing] {
    def apply(req: HttpRequest) = new NoSuchElementException("Empty reader.").toFutureException
  }

  /**
   * A const param.
   */
  object ConstReader {

    /**
     * Creates a ''RequestReader'' that reads given ''const'' param from the request.
     *
     * @return a const param value
     */
    def apply[A](const: A) = new RequestReader[A] {
      def apply(req: HttpRequest) = const.toFuture
    }
  }

  private[this] object StringToValueOrFail {
    def apply[A](param: String, rule: String)(number: => A) = new RequestReader[A] {
      def apply(req: HttpRequest) =
        try number.toFuture
        catch { case _: IllegalArgumentException => ValidationFailed(param, rule).toFutureException }
    }
  }

  private[this] object SomeStringToSomeValue {
    def apply[A](fn: String => A)(o: Option[String]) = o.flatMap { s =>
      try Some(fn(s))
      catch { case _: IllegalArgumentException => None }
    }
  }

  private[this] object StringsToValues {
    def apply[A](fn: String => A)(l: List[String]) = l.flatMap { s =>
      try List(fn(s))
      catch { case _: IllegalArgumentException => Nil }
    }
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
    def apply(param: String) = new RequestReader[String] {
      def apply(req: HttpRequest) = req.params.get(param) match {
        case Some("") => ValidationFailed(param, "should not be empty").toFutureException
        case Some(value) => value.toFuture
        case None => ParamNotFound(param).toFutureException
      }
    }
  }

  /**
   * A required integer param.
   */
  object RequiredIntParam {

    /**
     * Creates a ''RequestReader'' that reads a required integer ''param''
     * from the request or raises an exception when the param is missing or empty
     * or doesn't correspond to an expected type.
     *
     * @param param the param to read
     *
     * @return a param value
     */
    def apply(param: String) = for {
      s <- RequiredParam(param)
      n <- StringToValueOrFail(param, "should be integer")(s.toInt)
    } yield n
  }

  /**
   * A required long param.
   */
  object RequiredLongParam {

    /**
     * Creates a ''RequestReader'' that reads a required long ''param''
     * from the request or raises an exception when the param is missing or empty
     * or doesn't correspond to an expected type.
     *
     * @param param the param to read
     *
     * @return a param value
     */
    def apply(param: String) = for {
      s <- RequiredParam(param)
      n <- StringToValueOrFail(param, "should be long")(s.toLong)
    } yield n
  }

  /**
   * A required boolean param.
   */
  object RequiredBooleanParam {

    /**
     * Creates a ''RequestReader'' that reads a required boolean ''param''
     * from the request or raises an exception when the param is missing or empty
     * or doesn't correspond to an expected type.
     *
     * @param param the param to read
     *
     * @return a param value
     */
    def apply(param: String) = for {
      s <- RequiredParam(param)
      n <- StringToValueOrFail(param, "should be boolean")(s.toBoolean)
    } yield n
  }

  /**
   * A required float param.
   */
  object RequiredFloatParam {

    /**
     * Creates a ''RequestReader'' that reads a required float ''param''
     * from the request or raises an exception when the param is missing or empty
     * or doesn't correspond to an expected type.
     *
     * @param param the param to read
     *
     * @return a param value
     */
    def apply(param: String) = for {
      s <- RequiredParam(param)
      n <- StringToValueOrFail(param, "should be float")(s.toFloat)
    } yield n
  }

  /**
   * A required double param.
   */
  object RequiredDoubleParam {

    /**
     * Creates a ''RequestReader'' that reads a required double ''param''
     * from the request or raises an exception when the param is missing or empty
     * or doesn't correspond to an expected type.
     *
     * @param param the param to read
     *
     * @return a param value
     */
    def apply(param: String) = for {
      s <- RequiredParam(param)
      n <- StringToValueOrFail(param, "should be double")(s.toDouble)
    } yield n
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
    def apply(param: String) = new RequestReader[Option[String]] {
      def apply(req: HttpRequest) = req.params.get(param).toFuture
    }
  }

  /**
   * An optional int param.
   */
  object OptionalIntParam {

    /**
     * Creates a ''RequestReader'' that reads an optional integer ''param''
     * from the request into an ''Option''.
     *
     * @param param the param to read
     *
     * @return an option that contains a param value or ''None'' if the param
     *         is empty or it doesn't correspond to the expected type
     */
    def apply(param: String) = for {
      o <- OptionalParam(param)
    } yield SomeStringToSomeValue(_.toInt)(o)
  }

  /**
   * An optional long param.
   */
  object OptionalLongParam {

    /**
     * Creates a ''RequestReader'' that reads an optional long ''param''
     * from the request into an ''Option''.
     *
     * @param param the param to read
     *
     * @return an option that contains a param value or ''None'' if the param
     *         is empty or it doesn't correspond to the expected type
     */
    def apply(param: String) = for {
      o <- OptionalParam(param)
    } yield SomeStringToSomeValue(_.toLong)(o)
  }

  /**
   * An optional boolean param.
   */
  object OptionalBooleanParam {

    /**
     * Creates a ''RequestReader'' that reads an optional boolean ''param''
     * from the request into an ''Option''.
     *
     * @param param the param to read
     *
     * @return an option that contains a param value or ''None'' if the param
     *         is empty or it doesn't correspond to the expected type
     */
    def apply(param: String) = for {
      o <- OptionalParam(param)
    } yield SomeStringToSomeValue(_.toBoolean)(o)
  }

  /**
   * An optional float param.
   */
  object OptionalFloatParam {

    /**
     * Creates a ''RequestReader'' that reads an optional float ''param''
     * from the request into an ''Option''.
     *
     * @param param the param to read
     *
     * @return an option that contains a param value or ''None'' if the param
     *         is empty or it doesn't correspond to the expected type
     */
    def apply(param: String) = for {
      o <- OptionalParam(param)
    } yield SomeStringToSomeValue(_.toFloat)(o)
  }

  /**
   * An optional double param.
   */
  object OptionalDoubleParam {

    /**
     * Creates a ''RequestReader'' that reads an optional double ''param''
     * from the request into an ''Option''.
     *
     * @param param the param to read
     *
     * @return an option that contains a param value or ''None'' if the param
     *         is empty or it doesn't correspond to the expected type
     */
    def apply(param: String) = for {
      o <- OptionalParam(param)
    } yield SomeStringToSomeValue(_.toDouble)(o)
  }

  /**
   * A param validation rule.
   */
  object ValidationRule {

    /**
     * Creates a ''RequestReader'' that raises a ''ParamValidationFailed'' exception
     * with message ''rule'' when the given ''predicated'' is evaluated as ''false''.
     *
     * @param param the param name to validate
     * @param rule the exception message
     * @param predicate the predicate to test
     *
     * @return nothing or exception
     */
    def apply(param: String, rule: String)(predicate: => Boolean) = new RequestReader[Unit] {
      def apply(req: HttpRequest) =
        if (predicate) Future.Done
        else ValidationFailed(param, rule).toFutureException
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
    def apply(param: String) = new RequestReader[List[String]] {
      def apply(req: HttpRequest) = req.params.getAll(param).toList.flatMap(_.split(",")) match {
        case Nil => ParamNotFound(param).toFutureException
        case unfiltered => unfiltered.filter(_ != "") match {
          case Nil => ValidationFailed(param, "should not be empty").toFutureException
          case filtered => filtered.toFuture
        }
      }
    }
  }

  /**
   * A required multi-value integer param.
   */
  object RequiredIntParams {

    /**
     * Creates a ''RequestReader'' that reads a required multi-value integer
     * ''param'' from the request into an ''List'' or raises an exception when the
     * param is missing or empty or doesn't correspond to an expected type.
     *
     * @param param the param to read
     *
     * @return a ''List'' that contains all the values of multi-value param
     */
    def apply(param: String) = for {
      ss <- RequiredParams(param)
      ns <- StringToValueOrFail(param, "should be integer")(ss.map { _.toInt })
    } yield ns
  }

  /**
   * A required multi-value long param.
   */
  object RequiredLongParams {

    /**
     * Creates a ''RequestReader'' that reads a required multi-value long
     * ''param'' from the request into an ''List'' or raises an exception when the
     * param is missing or empty or doesn't correspond to an expected type.
     *
     * @param param the param to read
     *
     * @return a ''List'' that contains all the values of multi-value param
     */
    def apply(param: String) = for {
      ss <- RequiredParams(param)
      ns <- StringToValueOrFail(param, "should be long")(ss.map { _.toLong })
    } yield ns
  }

  /**
   * A required multi-value boolean param.
   */
  object RequiredBooleanParams {

    /**
     * Creates a ''RequestReader'' that reads a required multi-value boolean
     * ''param'' from the request into an ''List'' or raises an exception when the
     * param is missing or empty or doesn't correspond to an expected type.
     *
     * @param param the param to read
     *
     * @return a ''List'' that contains all the values of multi-value param
     */
    def apply(param: String) = for {
      ss <- RequiredParams(param)
      ns <- StringToValueOrFail(param, "should be boolean")(ss.map { _.toBoolean })
    } yield ns
  }

  /**
   * A required multi-value float param.
   */
  object RequiredFloatParams {

    /**
     * Creates a ''RequestReader'' that reads a required multi-value float
     * ''param'' from the request into an ''List'' or raises an exception when the
     * param is missing or empty or doesn't correspond to an expected type.
     *
     * @param param the param to read
     *
     * @return a ''List'' that contains all the values of multi-value param
     */
    def apply(param: String) = for {
      ss <- RequiredParams(param)
      ns <- StringToValueOrFail(param, "should be float")(ss.map { _.toFloat })
    } yield ns
  }

  /**
   * A required multi-value double param.
   */
  object RequiredDoubleParams {

    /**
     * Creates a ''RequestReader'' that reads a required multi-value double
     * ''param'' from the request into an ''List'' or raises an exception when the
     * param is missing or empty or doesn't correspond to an expected type.
     *
     * @param param the param to read
     *
     * @return a ''List'' that contains all the values of multi-value param
     */
    def apply(param: String) = for {
      ss <- RequiredParams(param)
      ns <- StringToValueOrFail(param, "should be double")(ss.map { _.toDouble })
    } yield ns
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
    def apply(param: String) = new RequestReader[List[String]] {
      def apply(req: HttpRequest) = req.params.getAll(param).toList.flatMap(_.split(",")).filter(_ != "").toFuture
    }
  }

  /**
   * An optional multi-value integer param.
   */
  object OptionalIntParams {

    /**
     * Creates a ''RequestReader'' that reads an optional multi-value
     * integer ''param'' from the request into an ''List''.
     *
     * @param param the param to read
     *
     * @return a ''List'' that contains all the values of multi-value param or
     *         en empty list ''Nil'' if the param is missing or empty or doesn't
     *         correspond to a requested type.
     */
    def apply(param: String) = for {
      l <- OptionalParams(param)
    } yield StringsToValues(_.toInt)(l)
  }

  /**
   * An optional multi-value long param.
   */
  object OptionalLongParams {

    /**
     * Creates a ''RequestReader'' that reads an optional multi-value
     * integer ''param'' from the request into an ''List''.
     *
     * @param param the param to read
     *
     * @return a ''List'' that contains all the values of multi-value param or
     *         en empty list ''Nil'' if the param is missing or empty or doesn't
     *         correspond to a requested type.
     */
    def apply(param: String) = for {
      l <- OptionalParams(param)
    } yield StringsToValues(_.toLong)(l)
  }

  /**
   * An optional multi-value boolean param.
   */
  object OptionalBooleanParams {

    /**
     * Creates a ''RequestReader'' that reads an optional multi-value
     * boolean ''param'' from the request into an ''List''.
     *
     * @param param the param to read
     *
     * @return a ''List'' that contains all the values of multi-value param or
     *         en empty list ''Nil'' if the param is missing or empty or doesn't
     *         correspond to a requested type.
     */
    def apply(param: String) = for {
      l <- OptionalParams(param)
    } yield StringsToValues(_.toBoolean)(l)
  }

  /**
   * An optional multi-value float param.
   */
  object OptionalFloatParams {

    /**
     * Creates a ''RequestReader'' that reads an optional multi-value
     * float ''param'' from the request into an ''List''.
     *
     * @param param the param to read
     *
     * @return a ''List'' that contains all the values of multi-value param or
     *         en empty list ''Nil'' if the param is missing or empty or doesn't
     *         correspond to a requested type.
     */
    def apply(param: String) = for {
      l <- OptionalParams(param)
    } yield StringsToValues(_.toFloat)(l)
  }

  /**
   * An optional multi-value double param.
   */
  object OptionalDoubleParams {

    /**
     * Creates a ''RequestReader'' that reads an optional multi-value
     * double ''param'' from the request into an ''List''.
     *
     * @param param the param to read
     *
     * @return a ''List'' that contains all the values of multi-value param or
     *         en empty list ''Nil'' if the param is missing or empty or doesn't
     *         correspond to a requested type.
     */
    def apply(param: String) = for {
      l <- OptionalParams(param)
    } yield StringsToValues(_.toDouble)(l)
  }

  /**
   * A required header.
   */
  object RequiredHeader {

    /**
     * Creates a ''RequestReader'' that reads a required string ''header''
     * from the request or raises an exception when the param is missing or empty.
     *
     * @param header the header to read
     *
     * @return a header
     */
    def apply(header: String) = new RequestReader[String] {
      def apply(req: HttpRequest) = req.headerMap.get(header) match {
        case Some(value) => value.toFuture
        case None => HeaderNotFound(header).toFutureException
      }
    }
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
    def apply(header: String) = new RequestReader[Option[String]] {
      def apply(req: HttpRequest) = req.headerMap.get(header).toFuture
    }
  }

  /**
   * A ''RequestReader'' that reads the request body, interpreted as a ''Array[Byte]'',
   * or throws a ''BodyNotFound'' exception.
   */
  object RequiredBody extends RequestReader[Array[Byte]] {
    def apply(req: HttpRequest): Future[Array[Byte]] = OptionalBody(req).flatMap {
      case Some(body) => body.toFuture
      case None => BodyNotFound.toFutureException
    }
  }

  /**
   * A ''RequestReader'' that reads the request body, interpreted as a ''Array[Byte]'',
   * into an ''Option''.
   */
  object OptionalBody extends RequestReader[Option[Array[Byte]]]{
    def apply(req: HttpRequest): Future[Option[Array[Byte]]] = req.contentLength match {
      case Some(length) if length > 0 => Some(RequestBody(req)).toFuture
      case _ => Future.None
    }
  }

  /**
   * A ''RequestReader'' that reads the request body, interpreted as a ''String'',
   * or throws a ''BodyNotFound'' exception.
   */
  object RequiredStringBody extends RequestReader[String] {
    def apply(req: HttpRequest): Future[String] = for {
      b <- RequiredBody(req)
    } yield new String(b, "UTF-8")
  }

  /**
   * A ''RequestReader'' that reads the request body, interpreted as a ''String'',
   * into an ''Option''.
   */
  object OptionalStringBody extends RequestReader[Option[String]] {
    def apply(req: HttpRequest): Future[Option[String]] = for {
      b <- OptionalBody(req)
    } yield b match {
      case Some(body) => Some(new String(body, "UTF-8"))
      case None => None
    }
  }

  /**
   * A ''RequestReader'' that reads an optional json object serialized in request body into
   * an ''Option''.
   */
  class OptionalJsonBody[A](val decode: DecodeJson[A]) extends RequestReader[Option[A]] {
    def apply(req: HttpRequest): Future[Option[A]] = for {
      b <- OptionalStringBody(req)
    } yield b match {
      case Some(body) => decode(body)
      case None => None
    }
  }

  object OptionalJsonBody {
    def apply[A](implicit decode: DecodeJson[A]) = new OptionalJsonBody[A](decode)
    def apply[A](req: HttpRequest)(implicit decode: DecodeJson[A]) = (new OptionalJsonBody[A](decode))(req)
  }

  /**
   * A ''RequestReader'' that read a json object serialized ni request body.
   */
  class RequiredJsonBody[A](val decode: DecodeJson[A]) extends RequestReader[A] {
    def apply(req: HttpRequest): Future[A] = OptionalJsonBody(req)(decode) flatMap {
      case Some(json) => json.toFuture
      case None => JsonNotParsed.toFutureException
    }
  }

  object RequiredJsonBody {
    def apply[A](implicit decode: DecodeJson[A]) = new RequiredJsonBody[A](decode)
    def apply[A](req: HttpRequest)(implicit decode: DecodeJson[A]) = (new RequiredJsonBody[A](decode))(req)
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
    def apply(cookieName: String) = new RequestReader[Cookie] {
      def apply(req: HttpRequest): Future[Cookie] = req.cookies.get(cookieName) match {
        case None => CookieNotFound(cookieName).toFutureException
        case Some(cookie: Cookie) => cookie.toFuture
      }
    }
  }

  /**
   * An optional cookie
   */
  object OptionalCookie {
    /**
     * Creates a ''RequestReader'' that reads an optional cookie from the request
     *
     * @param cookieName the name of the cookie to read
     *
     * @return An option that contains a cookie or None if the cookie does not exist on the request.
     */
    def apply(cookieName: String) = new RequestReader[Option[Cookie]] {
      def apply(req: HttpRequest): Future[Option[Cookie]] = req.cookies.get(cookieName).toFuture
    }
  }
}
