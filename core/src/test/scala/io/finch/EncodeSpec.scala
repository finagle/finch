package io.finch

import com.twitter.io.Buf

import java.nio.charset.Charset
import java.util.UUID

class EncodeSpec extends FinchSpec {

  checkAll("Encode.Text[String]", EncodeLaws.text[String].all)
  checkAll("Encode.Text[Int]", EncodeLaws.text[Int].all)
  checkAll("Encode.Text[Option[Boolean]]", EncodeLaws.text[Option[Boolean]].all)
  checkAll("Encode.Text[List[Long]]", EncodeLaws.text[List[Long]].all)
  checkAll("Encode.Text[Either[UUID, Float]]", EncodeLaws.text[Either[UUID, Float]].all)

  it should "round trip Unit" in {
    check { cs: Charset =>
      implicitly[Encode[Unit]].apply((), cs) === Buf.Empty
    }
  }

  it should "round trip Buf" in {
    check { (cs: Charset, buf: Buf) =>
      implicitly[Encode[Buf]].apply(buf, cs) === buf
    }
  }

  it should "encode exceptions" in {
    check { (s: String, cs: Charset) =>
      val e = new Exception(s)
      val text = Encode[Exception, Text.Plain].apply(e, cs)

      text === Buf.ByteArray.Owned(s.getBytes(cs.name))
    }
  }
}
