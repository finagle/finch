package io.finch

import cats.Eq
import javax.activation.MimeType
import scala.util.control.NonFatal

case class Accept(primary: String, subtype: String)

object Accept {

  def fromString(s: String): Option[Accept] = try {
    val mt = new MimeType(s)
    Some(Accept(mt.getPrimaryType, mt.getSubType))
  } catch {
    case NonFatal(_) => None
  }

  implicit val eq: Eq[Accept] = Eq.instance((a, b) => {
    (a.primary == b.primary || a.primary == "*") &&
      (a.subtype == b.subtype || a.subtype == "*")
  })
}
