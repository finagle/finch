package io.finch.pickling

import com.twitter.io.Buf.Utf8
import com.twitter.io.Charsets
import com.twitter.util.Try
import io.finch.{Decode, Encode}
import org.scalacheck.Arbitrary._
import org.scalacheck.{Gen, Arbitrary}
import org.scalatest.Matchers
import org.scalatest.prop.Checkers

import scala.pickling.{PickleFormat, Pickle}
import scala.pickling.Defaults._

case class ExampleCaseClass(a: String, b: Int, c: Boolean)
case class ExampleNestedCaseClass(
                                   string: String,
                                   double: Double,
                                   long: Long,
                                   ints: List[Int],
                                   example: ExampleCaseClass
                                 )

object ExampleCaseClass {
  implicit val exampleCaseClassArbitrary: Arbitrary[ExampleCaseClass] = Arbitrary(
    for {
      a <- Gen.alphaStr
      b <- arbitrary[Int]
      c <- arbitrary[Boolean]
    } yield ExampleCaseClass(a, b, c)
  )
}

object ExampleNestedCaseClass {
  implicit val exampleNestedCaseClassArbitrary: Arbitrary[ExampleNestedCaseClass] = Arbitrary(
    for {
      s <- Gen.alphaStr
      d <- arbitrary[Double]
      l <- arbitrary[Long]
      i <- arbitrary[List[Int]]
      e <- arbitrary[ExampleCaseClass]
    } yield ExampleNestedCaseClass(s, d, l, i, e)
  )
}


trait PicklingSerialCodecProperties { self: Matchers with Checkers =>

  /**
    * Confirm that this encoder can encode instances of our case class.
    */
  def encodeNestedCaseClass(implicit ee: Encode.Json[ExampleNestedCaseClass],
                            f: String => Pickle,
                            format: PickleFormat
                           ): Unit =
    check { (e: ExampleNestedCaseClass) =>
      f((Utf8.unapply(ee(e, Charsets.Utf8)).get)).unpickle[ExampleNestedCaseClass].isInstanceOf[ExampleNestedCaseClass]
    }

  /**
    * Confirm that this encoder can decode instances of our case class.
    */
  def decodeNestedCaseClass(implicit decoder: Decode[ExampleNestedCaseClass],
                            f: Pickle => String,
                            ft: String => Pickle,
                            format: PickleFormat): Unit =
    check { (e: ExampleNestedCaseClass) =>
      ft(f(e.pickle)).unpickle[ExampleNestedCaseClass].isInstanceOf[ExampleNestedCaseClass]
    }

  /**
    * Confirm that this encoder fails on invalid input (both ill-formed JSON and
    * invalid JSON).
    */
  def failToDecodeInvalidJson(implicit decoder: Decode[ExampleNestedCaseClass],
                              f: String => Pickle,
                              ft: PickleFormat): Unit = {
    check { (badJson: String) =>
        decoder(badJson).isThrow && Try(f(badJson).unpickle[ExampleNestedCaseClass]).isThrow
    }

  }

  /**
    * Confirm that this encoder has the correct content type.
    */
  def checkContentType(implicit ee: Encode.Json[ExampleNestedCaseClass]): Unit = ()
}
