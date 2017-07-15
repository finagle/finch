package io.finch.iteratee

import com.twitter.finagle.http.Request
import com.twitter.io.{Buf, Writer}
import com.twitter.util._
import io.catbird.util._
import io.finch.{Application, EndpointResult, FinchSpec, Input}
import io.finch.internal._
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class EnumerateEndpointSpec extends FinchSpec with GeneratorDrivenPropertyChecks {

  "enumeratorBody" should "enumerate input stream" in {
    forAll { (data: List[Buf]) =>
      val req = Request()
      req.setChunked(chunked = true)
      write(data, req.writer)

      val Some(enumerator) =
        enumeratorBody[Buf, Application.OctetStream].apply(Input.fromRequest(req)).awaitValueUnsafe()

      Await.result(enumerator.toVector) should contain theSameElementsAs data
    }

  }

  "enumeratorBody" should "skip matching if request is not chunked" in {
    enumeratorBody[Buf, Application.OctetStream].apply(Input.fromRequest(Request())) shouldBe EndpointResult.Skipped
  }

  "enumeratorJsonBody" should "enumerate input stream if required Enumerate instance is presented" in {
    implicit val enumerate = Enumerate.instance[String, Application.Json]((enum, cs) => {
      enum.map(_.asString(cs))
    })

    forAll { (data: List[String]) =>
      val req = Request()
      req.setChunked(chunked = true)
      write(data.map(Buf.Utf8.apply), req.writer)

      val Some(enumerator) = enumeratorJsonBody[String].apply(Input.fromRequest(req)).awaitValueUnsafe()

      Await.result(enumerator.toVector) should contain theSameElementsAs data
    }
  }

  private def write(data: List[Buf], writer: Writer with Closable): Future[Unit] = {
    data match {
      case Nil => writer.close()
      case head :: tail => writer.write(head).foreach(_ => write(tail, writer))
    }
  }

}
