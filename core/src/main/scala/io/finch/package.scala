package io

import com.twitter.util.Future

/**
 * This is a root package of the Finch library, which provides an immutable layer of functions and types atop of Finagle
 * for writing lightweight HTTP services. It roughly contains three packages: [[io.finch.route]], [[io.finch.request]],
 * [[io.finch.response]].
 */
package object finch {

  /**
   * Alters any object within a `toFuture` method.
   *
   * @param any an object to be altered
   *
   * @tparam A an object type
   */
  implicit class AnyOps[A](val any: A) extends AnyVal {

    /**
     * Converts this ''any'' object into a ''Future''
     */
    def toFuture: Future[A] = Future.value[A](any)
  }

  /**
   * Alters any throwable with a `toFutureException` method.
   *
   * @param t a throwable to be altered
   */
  implicit class ThrowableOps(val t: Throwable) extends AnyVal {

    /**
     * Converts this throwable object into a `Future` exception.
     */
    def toFutureException[A]: Future[A] = Future.exception[A](t)
  }
}
