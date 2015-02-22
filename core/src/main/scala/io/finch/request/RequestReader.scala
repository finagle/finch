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

import com.twitter.util.{Throw, Return, Future}
import io.finch._
import io.finch.request.items._

/**
 * A request reader (a reader monad) that reads a [[com.twitter.util.Future Future]] of `A` from the HTTP request.
 */
trait RequestReader[A] { self =>

  /**
   * A [[io.finch.request.items.RequestItem RequestItem]] read by this request reader.
   */
  def item: RequestItem

  /**
   * Reads the data from given request `req`.
   *
   * @tparam Req the request type
   * @param req the request to read
   */
  def apply[Req](req: Req)(implicit ev: Req => HttpRequest): Future[A]

  /**
   * Flat-maps this request reader to the given function `A => RequestReader[B]`.
   */
  def flatMap[B](fn: A => RequestReader[B]): RequestReader[B] = new RequestReader[B] {
    val item = MultipleItems
    def apply[Req](req: Req)(implicit ev: Req => HttpRequest): Future[B] = self(req) flatMap { fn(_)(req) }
  }

  /**
   * Maps this request reader to the given function `A => B`.
   */
  def map[B](fn: A => B): RequestReader[B] = new RequestReader[B] {
    val item = self.item
    def apply[Req](req: Req)(implicit ev: Req => HttpRequest): Future[B] = self(req) map fn
  }

  /**
   * Flat-maps this request reader to the given function `A => Future[B]`.
   */
  def embedFlatMap[B](fn: A => Future[B]): RequestReader[B] = new RequestReader[B] {
    val item = self.item
    def apply[Req](req: Req)(implicit ev: Req => HttpRequest): Future[B] = self(req) flatMap fn
  }

  /**
   * Composes this request reader with the given `that` request reader.
   */
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

  /**
   * Applies the given filter `p` to this request reader.
   */
  def withFilter(p: A => Boolean): RequestReader[A] = self.should("not fail validation")(p)

  /**
   * Validates the result of this request reader using a `predicate`. The rule is used for error reporting.
   *
   * @param rule text describing the rule being validated
   * @param predicate returns true if the data is valid
   *
   * @return a request reader that will return the value of this reader if it is valid.
   *         Otherwise the future fails with a [[io.finch.request.NotValid NotValid]] error.
   */
  def should(rule: String)(predicate: A => Boolean): RequestReader[A] = embedFlatMap { a =>
    if (predicate(a)) a.toFuture
    else NotValid(self.item, "should " + rule).toFutureException
  }

  /**
   * Validates the result of this request reader using a `predicate`. The rule is used for error reporting.
   *
   * @param rule text describing the rule being validated
   * @param predicate returns false if the data is valid
   *
   * @return a request reader that will return the value of this reader if it is valid.
   *         Otherwise the future fails with a [[io.finch.request.NotValid NotValid]] error.
   */
  def shouldNot(rule: String)(predicate: A => Boolean): RequestReader[A] = should(s"not $rule.")(x => !predicate(x))

  /**
   * Validates the result of this request reader using a predefined `rule`. This method allows for rules to be reused
   * across multiple request readers.
   *
   * @param rule the predefined [[io.finch.request.ValidationRule ValidationRule]] that will return true if the data is
   *             valid
   *
   * @return a request reader that will return the value of this reader if it is valid.
   *         Otherwise the future fails with a [[io.finch.request.NotValid NotValid]] error.
   */
  def should(rule: ValidationRule[A]): RequestReader[A] = should(rule.description)(rule.apply)

  /**
   * Validates the result of this request reader using a predefined `rule`. This method allows for rules to be reused
   * across multiple request readers.
   *
   * @param rule the predefined [[io.finch.request.ValidationRule ValidationRule]] that will return false if the data is
   *             valid
   *
   * @return a request reader that will return the value of this reader if it is valid.
   *         Otherwise the future fails with a [[io.finch.request.NotValid NotValid]] error.
   */
  def shouldNot(rule: ValidationRule[A]): RequestReader[A] = shouldNot(rule.description)(rule.apply)
}

/**
 * Convenience methods for creating new reader instances.
 */
object RequestReader {

  /**
   * Creates a new [[io.finch.request.RequestReader RequestReader]] that always succeeds, producing the specified value.
   *
   * @param value the value the new reader should produce
   * @param item the request item (e.g. parameter, header) the value is associated with
   * @return a new reader that always succeeds, producing the specified value
   */
  def value[A](value: A, item: RequestItem = MultipleItems): RequestReader[A] =
    const[A](value.toFuture)

  /**
   * Creates a new [[io.finch.request.RequestReader RequestReader]] that always fails, producing the specified
   * exception.
   *
   * @param exc the exception the new reader should produce
   * @param item the request item (e.g. parameter, header) the value is associated with
   * @return a new reader that always fails, producing the specified exception
   */
  def exception[A](exc: Throwable, item: RequestItem = MultipleItems): RequestReader[A] =
    const[A](exc.toFutureException)

  /**
   * Creates a new [[io.finch.request.RequestReader RequestReader]] that always produces the specified value. It will
   * succeed if the given `Future` succeeds and fail if the `Future` fails.
   *
   * @param value the value the new reader should produce
   * @param item the request item (e.g. parameter, header) the value is associated with
   * @return a new reader that always produces the specified value
   */
  def const[A](value: Future[A], item: RequestItem = MultipleItems): RequestReader[A] =
    embed[A](item)(_ => value)

  /**
   * Creates a new [[io.finch.request.RequestReader RequestReader]] that reads the result from the request.
   *
   * @param item the request item (e.g. parameter, header) the value is associated with
   * @param f the function to apply to the request
   * @return a new reader that reads the result from the request
   */
  def apply[A](item: RequestItem)(f: HttpRequest => A): RequestReader[A] =
    embed[A](item)(f(_).toFuture)

  private[this] def embed[A](reqItem: RequestItem)(f: HttpRequest => Future[A]): RequestReader[A] =
    new RequestReader[A] {
      val item = reqItem
      def apply[Req](req: Req)(implicit ev: Req => HttpRequest): Future[A] = f(req)
    }
}
