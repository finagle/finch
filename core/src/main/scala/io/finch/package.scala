package io

/**
 * This is a root package of the Finch library, which provides an immutable layer of functions and
 * types atop of Finagle for writing lightweight HTTP services.
 */
package object finch extends Endpoints with Outputs with ValidationRules {

  @deprecated("Use io.finch.Decode instead", "0.11")
  type DecodeRequest[A] = Decode[A]

  @deprecated("Use io.finch.Encode instead", "0.11")
  type EncodeResponse[A] = Encode[A]

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
