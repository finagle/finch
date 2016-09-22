package io.finch.circe

import cats.syntax.show._
import com.twitter.util.{Return, Throw, Try}
import io.circe.Decoder
import io.circe.jawn.decode
import io.finch.{Decode, Error}

trait Decoders {

  /**
   * Maps a Circe's [[Decoder]] to Finch's [[Decode]].
   */
  implicit def decodeCirce[A](implicit d: Decoder[A]): Decode[A] = Decode.instance(s =>
    decode[A](s).fold[Try[A]](
      error => Throw[A](Error(error.show)),
      value => Return(value)
    )
  )
}
