package io.finch.internal

import com.twitter.util.Try
import javax.activation.MimeType

case class Accept(`type`: String, subtype: String) { self =>

  def matches(other: Accept): Boolean = {
    (self.`type` == other.`type` || self.`type` == "*") &&
      (self.subtype == other.subtype || self.subtype == "*")
  }

}

object Accept {

  def apply(s: String): Option[Accept] = Try {
    val mt = new MimeType(s)
    Accept(mt.getPrimaryType, mt.getSubType)
  }.toOption
}
