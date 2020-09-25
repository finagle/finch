package io.finch

import java.nio.charset.Charset

import com.twitter.io.Buf
import io.circe.{Json, Printer}

package object circe extends Encoders with Decoders {

  /**
    * Provides a [[Printer]] that drops null keys.
    */
  object dropNullValues extends Encoders with Decoders {
    private[this] val printer: Printer = Printer.noSpaces.copy(dropNullValues = true)
    override protected def print(json: Json, cs: Charset): Buf =
      Buf.ByteBuffer.Owned(printer.printToByteBuffer(json, cs))
  }

  /**
    * Provides a [[Printer]] that uses a simple form of feedback-controller to predict the
    * size of the printed message.
    */
  object predictSize extends Encoders with Decoders {
    private[this] val printer: Printer = Printer.noSpaces.copy(predictSize = true)
    override protected def print(json: Json, cs: Charset): Buf =
      Buf.ByteBuffer.Owned(printer.printToByteBuffer(json, cs))
  }

  object accumulating extends Encoders with AccumulatingDecoders
}
