package io.finch
package client

import argonaut._, Argonaut._
import com.twitter.io.Buf
import com.twitter.io.Buf.Utf8

trait DecodeResource[A] {
  def apply(buf: Buf): A 
}

object DecodeResource {

  implicit def decodeStringResource: DecodeResource[String] = new DecodeResource[String] {
    def apply(buf: Buf): String = asString(buf)
  }
  
  implicit def decodeJsonResource: DecodeResource[Json] = new DecodeResource[Json] {
    def apply(buf: Buf): Json = asString(buf)
      .parseOption
      .getOrElse(jEmptyObject)
  }
  
  def asString(buf: Buf): String = Utf8.unapply(buf) match {
    case Some(x) => x
    case None    => ""
  }
}
