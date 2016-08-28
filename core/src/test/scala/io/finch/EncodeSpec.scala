package io.finch

import java.nio.charset.Charset
import java.util.UUID

import com.twitter.io.Buf
import io.finch.internal.BufText

class EncodeSpec extends FinchSpec {
  checkAll("Encode.Text[String]", EncodeLaws.text[String].all)
  checkAll("Encode.Text[Int]", EncodeLaws.text[Int].all)
  checkAll("Encode.Text[Option[Boolean]]", EncodeLaws.text[Option[Boolean]].all)
  checkAll("Encode.Text[List[Long]]", EncodeLaws.text[List[Long]].all)
  checkAll("Encode.Text[Either[UUID, Float]]", EncodeLaws.text[Either[UUID, Float]].all)

  it should "round trip Unit" in {
    check { (ct: String, cs: Charset) =>
      Encode[Unit](ct).apply((), cs) === Buf.Empty
    }
  }

  it should "round trip Buf" in {
    check { (ct: String, cs: Charset, buf: Buf) =>
      Encode[Buf](ct).apply(buf, cs) === buf
    }
  }

  it should "encode exceptions" in {
    check { (s: String, cs: Charset) =>
      val e = new Exception(s)

      val json = Encode[Exception]("application/json").apply(e, cs)
      val text = Encode[Exception]("text/plain").apply(e, cs)

      json === BufText(s"""{"message":"$s"}""", cs) && text === BufText(s, cs)
    }
  }
}
