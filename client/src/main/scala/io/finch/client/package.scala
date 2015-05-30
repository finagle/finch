package io.finch

import argonaut._, Argonaut._
import cats.data.Xor
import com.twitter.finagle.httpx.path.Path
import com.twitter.io.Buf
import com.twitter.io.Buf._
import com.twitter.util.Future

package object client {

  type Result = String Xor Resource

  implicit def decodeStrResource: DecodeResource[String] = new DecodeResource[String] {
    def apply(r: Resource): String = asString(r) 
  }

  implicit def decodeJsonResource: DecodeResource[Json] = new DecodeResource[Json] {
    def apply(r: Resource): Json = asString(r).parseOption.getOrElse(jEmptyObject)
  }

  private[client] def asString(r: Resource): String = Utf8.unapply(r.body) match {
    case Some(x) => x
    case None    => ""
  }

  case class Resource(body: Buf) {
    def as[A](implicit d: DecodeResource[A]): A = d(this)
  }

  trait DecodeResource[A] {
    def apply(resource: Resource): A 
  }

  case class FinchRequest(conn: Connection, path: Path)
}
