package io.finch.response

import com.twitter.io.Buf
import com.twitter.io.Buf.Utf8
import org.scalatest.{FlatSpec, Matchers}

import scala.language.implicitConversions
import scala.math._

class EncodeSpec extends FlatSpec with Matchers {

  private def encode[A](obj: A)(implicit e: EncodeResponse[A]): Buf = e(obj)
  "A EncodeJson" should "be accepted as implicit instance of subclass" in {
    implicit def seqEncodeJson[A](implicit ea: EncodeResponse[A]): EncodeResponse[Seq[A]] =
      EncodeResponse.fromString[Seq[A]]("application/json") {
        seq => seq.map { e => Utf8.unapply(ea(e)).getOrElse("") }.mkString("[", ", ", "]")
      }

    implicit val scalaNumberEncodeJson = EncodeResponse.fromString[ScalaNumber]("application/json")(s => s.toString)

    Utf8.unapply(encode(Seq(BigDecimal(123l), BigDecimal(0l)))) shouldBe Some("[123, 0]")
    Utf8.unapply(encode(Vector(BigDecimal(123l), BigDecimal(0l)))) shouldBe Some("[123, 0]")
    Utf8.unapply(encode(List(BigInt(123l), BigInt(0l)))) shouldBe Some("[123, 0]")
  }
}