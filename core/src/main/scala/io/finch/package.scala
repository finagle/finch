package io

import com.twitter.util.Future

/**
 * This is a root package of the Finch library, which provides an immutable layer of functions and types atop of Finagle
 * for writing lightweight HTTP services.
 */
package object finch extends Endpoints with Outputs with RequestReaders with ValidationRules {

  /**
   * Representations for the various types that can be processed with [[RequestReader]]s.
   */
  object items {
    sealed abstract class RequestItem(val kind: String, val nameOption:Option[String] = None) {
      val description = kind + nameOption.fold("")(" '" + _ + "'")
    }
    final case class ParamItem(name: String) extends RequestItem("param", Some(name))
    final case class HeaderItem(name: String) extends RequestItem("header", Some(name))
    final case class CookieItem(name: String) extends RequestItem("cookie", Some(name))
    case object BodyItem extends RequestItem("body")
    case object MultipleItems extends RequestItem("request")
  }

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
