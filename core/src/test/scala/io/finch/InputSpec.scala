package io.finch

import java.nio.charset.Charset

import com.twitter.finagle.http.Method
import com.twitter.io.Buf
import org.scalacheck.Prop
import org.scalacheck.Prop.forAll

class InputSpec extends FinchSpec {

  behavior of "Input"

  it should "properly construct Inputs using factories with params for the different methods" in {
    def validateInput(
        input: Input, method: Method, segments: Seq[String], params: Map[String, String]): Prop = {
      input.request.method === method
      input.request.path === (segments mkString "/")
      input.request.params === params
      input.path === segments
    }
    check { ps: Params =>
      forAll(genPath) { p: String =>
        val segments = p.split("/").toList.drop(1)
        validateInput(Input.get(p, ps.p.toSeq: _*), Method.Get, segments, ps.p)
        validateInput(Input.put(p, ps.p.toSeq: _*), Method.Put, segments, ps.p)
        validateInput(Input.patch(p, ps.p.toSeq: _*), Method.Patch, segments, ps.p)
        validateInput(Input.delete(p, ps.p.toSeq: _*), Method.Delete, segments, ps.p)
      }
    }
  }

  it should "add content through withContent" in {
    check { (i: Input, b: Buf, cs: Charset) =>
      i.withBody(b).request.content === b
      i.withBody(b, Some(cs)).request.content === b
    }
  }

  it should "add headers through withHeaders" in {
    check { (i: Input, hs: Headers) =>
      val hm = i.withHeaders(hs.m.toSeq: _*).request.headerMap
      hs.m.forall { case (k, v) =>
        hm.contains(k)
        hm(k) === v
      }
    }
  }

  it should "add form elements through withForm" in {
    check { (i: Input, ps: Params) =>
      intercept[IllegalArgumentException](i.withForm(ps.p.toSeq: _*))
      i.request.host = "http://www.google.com"
      if (ps.p.isEmpty) {
        intercept[IllegalArgumentException](i.withForm(ps.p.toSeq: _*))
        true
      } else {
        val input = i.withForm(ps.p.toSeq: _*)
        ps.p.forall { case (k, v) => input.request.contentString.contains(s"$k=$v") }
      }
    }
  }
}
