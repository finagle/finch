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

