package io

import shapeless.Witness

/**
 * This is a root package of the Finch library, which provides an immutable layer of functions and
 * types atop of Finagle for writing lightweight HTTP services.
 */
package object finch extends Endpoints
    with Outputs
    with ValidationRules
    with io.finch.syntax.DeprecatedEndpointMappers {

  object items {
    sealed abstract class RequestItem(val kind: String, val nameOption:Option[String] = None) {
      val description = kind + nameOption.fold("")(" '" + _ + "'")
    }
    final case class AttributeItem(name: String) extends RequestItem("attribute", Some(name))
    final case class ParamItem(name: String) extends RequestItem("param", Some(name))
    final case class HeaderItem(name: String) extends RequestItem("header", Some(name))
    final case class CookieItem(name: String) extends RequestItem("cookie", Some(name))
    case object BodyItem extends RequestItem("body")
    case object MultipleItems extends RequestItem("request")
  }

  object Application {
    type Json = Witness.`"application/json"`.T
    type Xml = Witness.`"application/xml"`.T
    type AtomXml = Witness.`"application/atom+xml"`.T
    type Csv = Witness.`"application/csv"`.T
    type Javascript = Witness.`"application/javascript"`.T
    type OctetStream = Witness.`"application/octet-stream"`.T
    type RssXml = Witness.`"application/rss+xml"`.T
    type WwwFormUrlencoded = Witness.`"application/x-www-form-urlencoded"`.T
  }

  object Text {
    type Plain = Witness.`"text/plain"`.T
    type Html = Witness.`"text/html"`.T
    type EventStream = Witness.`"text/event-stream"`.T
  }
}
