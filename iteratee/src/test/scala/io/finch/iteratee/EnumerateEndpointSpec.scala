package io.finch.iteratee

import cats.effect.IO
import com.twitter.finagle.http.Request
import com.twitter.io.{Buf, Writer}
import com.twitter.util._
import io.finch.{Application, EndpointResult, FinchSpec, Input}
import io.finch.catsEffect.E
import io.finch.internal._
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class EnumerateEndpointSpec extends FinchSpec with GeneratorDrivenPropertyChecks {

  private implicit val enumerateString = Enumerate.instance[IO, String, Application.Json]((enum, cs) => {
    enum.map(_.asString(cs))
  })

  "enumeratorBody" should "enumerate input stream" in {
    forAll { (data: List[Buf]) =>
      val req = Request()
      req.setChunked(chunked = true)
      write(data, req.writer)

      val Some(enumerator) =
        enumeratorBody[IO, Buf, Application.OctetStream].apply(Input.fromRequest(req)).awaitValueUnsafe()

      enumerator.toVector.unsafeRunSync() should contain theSameElementsAs data
    }

  }

  "enumeratorBody.toString" should "be correct" in {
    enumeratorBody[IO, Buf, Application.OctetStream].toString shouldBe "enumeratorBody"
  }

  "enumeratorBody" should "skip matching if request is not chunked" in {
    enumeratorBody[IO, Buf, Application.OctetStream].apply(Input.fromRequest(Request())) shouldBe
      EndpointResult.NotMatched
  }

  "enumeratorJsonBody" should "enumerate input stream if required Enumerate instance is presented" in {
    forAll { (data: List[String]) =>
      val req = Request()
      req.setChunked(chunked = true)
      write(data.map(Buf.Utf8.apply), req.writer)

      val Some(enumerator) = enumeratorJsonBody[IO, String].apply(Input.fromRequest(req)).awaitValueUnsafe()

      enumerator.toVector.unsafeRunSync() should contain theSameElementsAs data
    }
  }

  "enumeratorJsonBody.toString" should "be correct" in {
    enumeratorJsonBody[IO, Buf].toString shouldBe "enumeratorJsonBody"
  }

  private def write(data: List[Buf], writer: Writer[Buf] with Closable): Future[Unit] = {
    data match {
      case Nil => writer.close()
      case head :: tail => writer.write(head).foreach(_ => write(tail, writer))
    }
  }

}
