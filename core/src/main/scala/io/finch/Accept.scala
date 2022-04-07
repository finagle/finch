package io.finch

import shapeless.Witness

import java.util.Locale

/** Models an HTTP Accept header (see RFC2616, 14.1).
  *
  * @note
  *   This API doesn't validate the input primary/sub types.
  *
  * @see
  *   https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
  */
abstract class Accept {
  def primary: String
  def sub: String
  def matches[CT <: String](implicit m: Accept.Matcher[CT]): Boolean = m(this)

  override def toString: String = s"Accept: $primary/$sub"
}

object Accept {

  private object Empty extends Accept {
    def primary: String = ""
    def sub: String = ""
    override def matches[CT <: String](implicit m: Matcher[CT]): Boolean = false
  }

  abstract class Matcher[CT <: String] {
    def apply(a: Accept): Boolean
  }

  object Matcher {

    private object Empty extends Matcher[Nothing] {
      def apply(a: Accept): Boolean = false
    }

    implicit val json: Matcher[Application.Json] = fromWitness[Application.Json]
    implicit val xml: Matcher[Application.Xml] = fromWitness[Application.Xml]
    implicit val text: Matcher[Text.Plain] = fromWitness[Text.Plain]
    implicit val html: Matcher[Text.Html] = fromWitness[Text.Html]

    implicit def fromWitness[CT <: String](implicit w: Witness.Aux[CT]): Matcher[CT] = {
      val slashIndex = w.value.indexOf(47)
      if (slashIndex == 0 || slashIndex == w.value.length) Empty.asInstanceOf[Matcher[CT]]
      else
        new Matcher[CT] {
          private val primary: String = w.value.substring(0, slashIndex).trim.toLowerCase(Locale.ENGLISH)
          private val sub: String = w.value.substring(slashIndex + 1, w.value.length).trim.toLowerCase(Locale.ENGLISH)
          def apply(a: Accept): Boolean =
            (a.primary == "*" && a.sub == "*") || (a.primary == primary && (a.sub == sub || a.sub == "*"))
        }
    }
  }

  /** Parses an [[Accept]] instance from a given string. Returns `null` when not able to parse.
    */
  def fromString(s: String): Accept = {
    // Adopted from Java's MimeType's API.
    val slashIndex = s.indexOf(47)
    val semIndex = s.indexOf(59)
    val length = if (semIndex < 0) s.length else semIndex

    if (slashIndex < 0 || slashIndex >= length) Empty
    else
      new Accept {
        val primary: String = s.substring(0, slashIndex).trim.toLowerCase(Locale.ENGLISH)
        val sub: String = s.substring(slashIndex + 1, length).trim.toLowerCase(Locale.ENGLISH)
      }
  }
}
