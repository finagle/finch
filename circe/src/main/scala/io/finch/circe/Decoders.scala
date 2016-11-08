package io.finch.circe

import cats.syntax.show._
import com.twitter.util.{Return, Throw, Try}
import io.circe.Decoder
import io.circe.jawn.decode
import io.finch.{Decode, Error}
import io.finch.internal.BufText

trait Decoders {

  /**
   * Maps a Circe's [[Decoder]] to Finch's [[Decode]].
   */
  implicit def decodeCirce[A: Decoder]: Decode.Json[A] = Decode.json((b, cs) =>

    // TODO: Eliminate toString conversion
    // See https://github.com/finagle/finch/issues/511
    // Jawn can parse from ByteBuffer's if they represent UTF-8 strings.
    // We can check the charset here and do parsing w/o extra to-string conversion.
    decode[A](BufText.extract(b, cs)).fold[Try[A]](
      error => Throw[A](Error(error.show)),
      value => Return(value)
    )
  )
}
