package io.finch

import com.twitter.util.Try
import io.finch.request.DecodeRequest
import io.finch.response.EncodeResponse
import org.json4s._
import org.json4s.jackson.JsonMethods
import org.json4s.jackson.Serialization._

package object json4s {

  /**
   * @param formats json4s `Formats` to use for decoding
   * @tparam A the type of data to decode into
   */
  //@TODO get rid of Manifest as soon as json4s migrates to new reflection API
  implicit def decodeJson[A : Manifest](implicit formats: Formats): DecodeRequest[A] = DecodeRequest(
    input => Try(JsonMethods.parse(input).extract[A])
  )

  /**
   * @param formats json4s `Formats` to use for decoding
   * @tparam A the type of data to encode
   * @return
   */
  implicit def encodeJson[A <: AnyRef](implicit formats: Formats): EncodeResponse[A] =
    EncodeResponse("application/json") { out: A =>
      write(out)
    }
}
