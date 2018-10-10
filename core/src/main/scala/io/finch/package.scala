package io

import cats.effect.IO

/**
 * This is a root package of the Finch library, which provides an immutable layer of functions and
 * types atop of Finagle for writing lightweight HTTP services.
 */
package object finch extends Outputs with ValidationRules {

  object catsEffect extends EndpointModule[IO]

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

}
