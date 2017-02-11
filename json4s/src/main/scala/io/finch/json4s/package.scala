package io.finch

import com.twitter.io.Buf
import com.twitter.util.Try
import io.finch.internal.HttpContent
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization._

package object json4s {

  implicit def decodeJson[A : Manifest](implicit formats: Formats): Decode.Json[A] =
    Decode.json((b, cs) => Try(parse(b.asString(cs)).extract[A]))

  implicit def encodeJson[A <: AnyRef](implicit formats: Formats): Encode.Json[A] =
    Encode.json((a, cs) => Buf.ByteArray.Owned(write(a).getBytes(cs)))

  implicit val encodeJsonException: Encode.Json[Exception] = Encode.json((a, cs) =>
    Buf.ByteArray.Owned(
      compact(render(JObject("message" -> JString(a.getMessage)))).getBytes(cs.name)
    )
  )
}
