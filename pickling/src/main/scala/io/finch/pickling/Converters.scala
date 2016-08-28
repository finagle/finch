package io.finch.pickling


import java.util.Base64

import scala.pickling.Pickle
import scala.pickling.binary.BinaryPickle
import scala.pickling.json.JSONPickle

object Converters {

  private def byteMaker(input: String) = {
    Base64.getDecoder.decode(input)
  }

  private def byteBreaker(input: Array[Byte]) = {
    Base64.getEncoder.encodeToString(input)
  }


  implicit def jsonStringReader(input: String): JSONPickle =
    scala.pickling.json.JSONPickle(input)

  implicit def binaryStringReader(input: String)(implicit f: String => Array[Byte]): BinaryPickle =
    scala.pickling.binary.BinaryPickle(byteMaker(input))

  implicit def pickleStringWriter(input: Pickle): String = input match {
    case x: JSONPickle => x.value.toString
    case x: BinaryPickle => byteBreaker(x.value)
  }

}
