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
import io.finch.request.items._

/**
 * Convenience methods for creating new reader instances.
 */
object RequestReader {

  /**
   * Creates a new [[io.finch.request.RequestReader RequestReader]] that always succeeds, producing the specified value.
   *
   * @param value the value the new reader should produce
   * @return a new reader that always succeeds, producing the specified value
   */
  def value[A](value: A): RequestReader[A] = const[A](value.toFuture)

  /**
   * Creates a new [[io.finch.request.RequestReader RequestReader]] that always fails, producing the specified
   * exception.
   *
   * @param exc the exception the new reader should produce
   * @return a new reader that always fails, producing the specified exception
   */
  def exception[A](exc: Throwable): RequestReader[A] = const[A](exc.toFutureException[A])

  /**
   * Creates a new [[io.finch.request.RequestReader RequestReader]] that always produces the specified value. It will
   * succeed if the given `Future` succeeds and fail if the `Future` fails.
   *
   * @param value the value the new reader should produce
   * @return a new reader that always produces the specified value
   */
  def const[A](value: Future[A]): RequestReader[A] = embed[HttpRequest, A](MultipleItems)(_ => value)

  /**
   * Creates a new [[io.finch.request.RequestReader RequestReader]] that reads the result from the request.
   *
   * @param f the function to apply to the request
   * @return a new reader that reads the result from the request
   */
  def apply[R, A](f: R => A): PRequestReader[R, A] = embed[R, A](MultipleItems)(f(_).toFuture)

  private[request] def embed[R, A](i: RequestItem)(f: R => Future[A]): PRequestReader[R, A] =
    new PRequestReader[R, A] {
      val item = i
      def apply(req: R): Future[A] = f(req)
    }

  import scala.reflect.ClassTag
  import shapeless._, labelled.{FieldType, field}

  class GenericDerivation[A] {
    def fromParams[Repr <: HList](implicit
      gen: LabelledGeneric.Aux[A, Repr],
      fp: FromParams[Repr]
    ): RequestReader[A] = fp.reader.map(gen.from)
  }

  trait FromParams[L <: HList] {
    def reader: RequestReader[L]
  }

  object FromParams {
    implicit val hnilFromParams: FromParams[HNil] = new FromParams[HNil] {
      def reader: RequestReader[HNil] = value(HNil)
    }

    implicit def hconsFromParams[HK <: Symbol, HV, T <: HList](implicit
      dh: DecodeRequest[HV],
      ct: ClassTag[HV],
      key: Witness.Aux[HK],
      fpt: FromParams[T]
    ): FromParams[FieldType[HK, HV] :: T] = new FromParams[FieldType[HK, HV] :: T] {
      def reader: RequestReader[FieldType[HK, HV] :: T] =
        param(key.value.name).as(dh, ct).map(field[HK](_)) :: fpt.reader
    }
  }

  def derive[A]: GenericDerivation[A] = new GenericDerivation[A]
}
