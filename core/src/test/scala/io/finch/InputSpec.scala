package io.finch

import java.nio.charset.Charset

import com.twitter.finagle.http.Method
import com.twitter.io.Buf
import io.finch.internal.BufText

class InputSpec extends FinchSpec {

  behavior of "Input"

  it should "properly construct Inputs using factories with params for the different methods" in {

    def validateInput(
      input: Input,
      method: Method,
      segments: Seq[String],
      params: Map[String, String]
    ): Boolean =
      input.request.method === method &&
      input.request.path === "/" + segments.mkString("/") &&
      input.request.params === params &&
      input.path === segments

    check { (ps: Params, p: Path) =>
      val segments = p.p.split("/").toList.drop(1)

      validateInput(Input.get(p.p, ps.p.toSeq: _*), Method.Get, segments, ps.p)
      validateInput(Input.put(p.p, ps.p.toSeq: _*), Method.Put, segments, ps.p)
      validateInput(Input.patch(p.p, ps.p.toSeq: _*), Method.Patch, segments, ps.p)
      validateInput(Input.delete(p.p, ps.p.toSeq: _*), Method.Delete, segments, ps.p)
    }
  }

  it should "add content through withBody" in {
    check { (i: Input, b: Buf) =>
      i.withBody[Text.Plain](b).request.content === b
    }
  }

  it should "add content corresponding to a class through withBody[JSON]" in {
    implicit val encodeException: Encode.Json[Exception] = Encode.json(
      (a, cc) => BufText(s"""{"message":"${a.getMessage}"}""", cc)
    )

    check { (i: Input, s: String, cs: Charset) =>
      val input = i.withBody[Application.Json](new Exception(s), Some(cs))

      input.request.content === BufText(s"""{"message":"$s"}""", cs) &&
      input.request.contentType === Some(s"application/json;charset=${cs.displayName.toLowerCase}")
    }
  }

  it should "add headers through withHeaders" in {
    check { (i: Input, hs: Headers) =>
      val hm = i.withHeaders(hs.m.toSeq: _*).request.headerMap
      hs.m.forall { case (k, v) => hm.contains(k) && hm(k) === v}
    }
  }

  it should "add form elements through withForm" in {
    check { (i: Input, ps: Params) =>
      ps.p.isEmpty || {
        val input = i.withForm(ps.p.toSeq: _*)
        val contentString = input.request.contentString
        ps.p.forall { case (k, v) => contentString.contains(s"$k=$v") } &&
        input.request.contentType === Some("application/x-www-form-urlencoded;charset=utf-8")
      }
    }
  }
}
