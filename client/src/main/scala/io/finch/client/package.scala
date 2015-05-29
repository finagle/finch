package io.finch

import argonaut._, Argonaut._
import com.twitter.finagle.httpx.path.Path

package object client {

  implicit def decodeStrResource: DecodeResource[String] = new DecodeResource[String] {
      def apply(r: Resource): String = r.body 
    }

  implicit def decodeJsonResource: DecodeResource[Json] = new DecodeResource[Json] {
    def apply(r: Resource): Json = r.body.parseOption.getOrElse(jEmptyObject)
  }  
  
  case class Resource(body: String) {
    def as[A](implicit d: DecodeResource[A]): A = d(this)
  }

  trait DecodeResource[A] {
    def apply(resource: Resource): A 
  }

  case class FinchRequest(conn: Connection, path: Path)
}
