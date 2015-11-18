package io.finch.circe

import com.twitter.util.{Return, Throw, Try}
import io.circe.Decoder
import io.circe.jawn.decode
import io.finch.{DecodeRequest, Error}

trait Decoders {

  /**
   * Maps a Circe's [[Decoder]] to Finch's [[DecodeRequest]].
   */
  implicit def decodeCirce[A](implicit d: Decoder[A]): DecodeRequest[A] = DecodeRequest.instance(s =>
    decode[A](s).fold[Try[A]](
      error => Throw[A](Error(error.getMessage)),
      value => Return(value)
    )
  )
}
