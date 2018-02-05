package io.finch

import javax.activation.MimeType
import scala.util.control.NonFatal

/**
 * Models an HTTP Accept header (see RFC2616, 14.1).
 *
 * @see https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
 */
final case class Accept(primary: String, sub: String) {
  def matches(that: Accept): Boolean =
    (this.primary == that.primary || this.primary == "*") &&
      (this.sub == that.sub || this.sub == "*")
}

object Accept {

  /**
   * Parses an [[Accept]] instance from a given string. Returns `null` when not able to parse.
   */
  def fromString(s: String): Accept = try {
    val mt = new MimeType(s)
    Accept(mt.getPrimaryType, mt.getSubType)
  } catch { case NonFatal(_) => null }
}
