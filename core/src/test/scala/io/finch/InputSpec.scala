package io.finch

import java.nio.charset.Charset

import cats.effect.IO
import com.twitter.finagle.http.Method
import com.twitter.io.{Buf, Pipe, Reader}
import com.twitter.util.{Await, Future}
import io.finch.data.Foo
import io.finch.internal.HttpContent

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
      input.route === segments

    check { (ps: Params, p: Path) =>
      val segments = p.p.split("/").toList.drop(1)

      validateInput(Input.get(p.p, ps.p.toSeq: _*), Method.Get, segments, ps.p)
      validateInput(Input.put(p.p, ps.p.toSeq: _*), Method.Put, segments, ps.p)
      validateInput(Input.patch(p.p, ps.p.toSeq: _*), Method.Patch, segments, ps.p)
      validateInput(Input.delete(p.p, ps.p.toSeq: _*), Method.Delete, segments, ps.p)
    }
  }

  it should "add fully-buffered content via withBody" in {
    check { (i: Input, b: Buf) =>
      i.withBody[Text.Plain](b).request.content === b
    }
  }

  it should "add chunked content via withBody" in {
    type ListStream[F[_], A] = List[A]
    implicit def listToReader[CT <: String]: EncodeStream.Aux[IO, ListStream, Buf, CT] =
      new EncodeStream[IO, ListStream, Buf] {
        type ContentType = CT

        def apply(s: ListStream[IO, Buf], cs: Charset): IO[Reader[Buf]] = {
          val p = new Pipe[Buf]

          def loop(from: List[Buf]): Future[Unit] = from match {
            case h :: t => p.write(h).before(loop(t))
            case _ => p.close()
          }

          loop(s)
          IO.pure(p)
        }
      }

    check { (i: Input, s: List[Buf]) =>
      val out = i.withBody[Application.OctetStream].apply[IO, ListStream, Buf](s).request.reader
      s.forall(buf => buf == Await.result(out.read()).get)
    }
  }

  it should "add content corresponding to a class through withBody[JSON]" in {
    check { (i: Input, f: Foo, cs: Charset) =>
      val input = i.withBody[Application.Json](f, cs)

      input.request.content.asString(cs) === s"""{s:"${f.s}"""" &&
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

  it should "parse route correctly" in {
    check { i: Input =>
      i.route === i.request.path.split("/").toList.drop(1)
    }
  }
}
