package io.finch

import com.twitter.concurrent.AsyncStream
import com.twitter.finagle.http.{Fields, Request}
import com.twitter.io.Buf
import com.twitter.util.Await
import java.nio.charset.StandardCharsets
import shapeless.{:+:, CNil}

class ToResponseSpec extends FinchSpec {

  behavior of "ToResponse"

  type AllContentTypes = Application.Json :+: Application.AtomXml :+: Application.Csv :+:
    Application.Javascript :+: Application.OctetStream :+: Application.RssXml :+:
    Application.WwwFormUrlencoded :+: Application.Xml :+: Text.Plain :+: Text.Html :+: Text.EventStream :+: CNil

  private implicit def encode[A, CT <: String]: Encode.Aux[A, CT] = Encode.instance((_, _) => Buf.Utf8("foo"))

  private val allContentTypes = Seq(
    "application/json",
    "application/atom+xml",
    "application/csv",
    "application/javascript",
    "application/octet-stream",
    "application/rss+xml",
    "application/x-www-form-urlencoded",
    "application/xml",
    "text/plain",
    "text/html",
    "text/event-stream"
  )

  it should "pick correct instance for AsyncStream[Buf]" in {
    val tr: ToResponse.Aux[AsyncStream[Buf], Text.Plain] = implicitly

    check { (chunks: List[Buf]) =>
      val in = AsyncStream.fromSeq(chunks)
      val out = AsyncStream.fromReader(tr(in, StandardCharsets.UTF_8, Seq.empty).reader)
      Await.result(in.toSeq) === Await.result(out.toSeq)
    }
  }

  it should "ignore Accept header when negotiation is not enabled" in {
    check { (req: Request) =>
      val s = Bootstrap.serve[AllContentTypes](*).toService
      val rep = Await.result(s(req))

      rep.contentType === Some("application/json")
    }
  }

  it should "ignore Accept header when single type is used for serve" in {
    check { (req: Request) =>
      val s = Bootstrap.serve[Text.Plain](*).configure(negotiateContentType = true).toService
      val rep = Await.result(s(req))

      rep.contentType === Some("text/plain")
    }
  }

  it should "respect Accept header when coproduct type is used for serve" in {
    check { (req: Request) =>
      val s = Bootstrap.serve[AllContentTypes](*).configure(negotiateContentType = true).toService
      val rep = Await.result(s(req))

      rep.contentType === req.accept.headOption
    }
  }

  it should "ignore order of values in Accept header and use first appropriate encoder in coproduct" in {
    check { (req: Request, accept: Accept) =>
      val a = s"${accept.primary}/${accept.subtype}"
      req.accept = a +: req.accept

      val s = Bootstrap.serve[AllContentTypes](*).configure(negotiateContentType = true).toService
      val rep = Await.result(s(req))

      val first = allContentTypes.collectFirst {
        case ct if req.accept.contains(ct) => ct
      }

      rep.contentType === first
    }
  }

  it should "select first encoder when Accept header is missing/empty" in {
    check { (req: Request) =>
      req.headerMap.remove(Fields.Accept)
      val s = Bootstrap.serve[AllContentTypes](*).configure(negotiateContentType = true).toService
      val rep = Await.result(s(req))

      rep.contentType === Some("application/json")
    }
  }

  it should "select last encoder when Accept header value doesn't match any existing encoder" in {
    check { (req: Request, accept: Accept) =>
      req.accept = s"${accept.primary}/foo"
      val s = Bootstrap.serve[AllContentTypes](*).configure(negotiateContentType = true).toService
      val rep = Await.result(s(req))

      rep.contentType === Some("text/event-stream")
    }
  }
}
