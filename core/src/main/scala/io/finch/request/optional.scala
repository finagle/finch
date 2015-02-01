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

package io.finch.request

import com.twitter.util.Future
import io.finch._
import items._
import scala.reflect.ClassTag

/**
 * An empty ''RequestReader''.
 */
object EmptyReader extends RequestReader[Nothing] {
  val item = MultipleItems
  def apply[Req](req: Req)(implicit ev: Req => HttpRequest) =
    new NoSuchElementException("Empty reader.").toFutureException
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
  def apply[A](const: Future[A]) = new RequestReader[A] {
    val item = MultipleItems
    def apply[Req](req: Req)(implicit ev: Req => HttpRequest) = const
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
  def apply(param: String): RequestReader[Int] = RequiredParam(param).as[Int]
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
  def apply(param: String): RequestReader[Long] = RequiredParam(param).as[Long]
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
  def apply(param: String): RequestReader[Boolean] = RequiredParam(param).as[Boolean]
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
  def apply(param: String): RequestReader[Float] = RequiredParam(param).as[Float]
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
  def apply(param: String): RequestReader[Double] = RequiredParam(param).as[Double]
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
  def apply(param: String): RequestReader[Option[Int]] = OptionalParam(param).as[Int]
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
  def apply(param: String): RequestReader[Option[Long]] = OptionalParam(param).as[Long]
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
  def apply(param: String): RequestReader[Option[Boolean]] = OptionalParam(param).as[Boolean]
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
  def apply(param: String): RequestReader[Option[Float]] = OptionalParam(param).as[Float]
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
  def apply(param: String): RequestReader[Option[Double]] = OptionalParam(param).as[Double]
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
  def apply(param: String): RequestReader[Seq[Int]] = RequiredParams(param).as[Int]
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
  def apply(param: String): RequestReader[Seq[Long]] = RequiredParams(param).as[Long]
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
  def apply(param: String): RequestReader[Seq[Boolean]] = RequiredParams(param).as[Boolean]
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
  def apply(param: String): RequestReader[Seq[Float]] = RequiredParams(param).as[Float]
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
  def apply(param: String): RequestReader[Seq[Double]] = RequiredParams(param).as[Double]
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
  def apply(param: String): RequestReader[Seq[Int]] = OptionalParams(param).as[Int]
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
  def apply(param: String): RequestReader[Seq[Long]] = OptionalParams(param).as[Long]
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
  def apply(param: String): RequestReader[Seq[Boolean]] = OptionalParams(param).as[Boolean]
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
  def apply(param: String): RequestReader[Seq[Float]] = OptionalParams(param).as[Float]
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
  def apply(param: String): RequestReader[Seq[Double]] = OptionalParams(param).as[Double]
}

  /**
 * A ''RequestReader'' that reads an optional encoded object serialized in request body
 * and decodes it, according to an implicit decoder, into an ''Option''.
 */
object OptionalBody {
  def apply[A](implicit m: DecodeMagnet[A], tag: ClassTag[A]): RequestReader[Option[A]] = OptionalStringBody.as[A]

  // TODO: Make it accept `Req` instead
  def apply[A](req: HttpRequest)(implicit m: DecodeMagnet[A], tag: ClassTag[A]): Future[Option[A]] =
    OptionalBody[A](m, tag)(req)
}

/**
 * A ''RequestReader'' that reads an encoded object serialized in request body
 * and decodes it according to an implicit decoder.
 */
object RequiredBody {
  def apply[A](implicit m: DecodeMagnet[A], tag: ClassTag[A]): RequestReader[A] = OptionalStringBody.as[A].failIfEmpty
  
  // TODO: Make it accept `Req` instead
  def apply[A](req: HttpRequest)(implicit m: DecodeMagnet[A], tag: ClassTag[A]): Future[A] = RequiredBody[A](m, tag)(req)
}
