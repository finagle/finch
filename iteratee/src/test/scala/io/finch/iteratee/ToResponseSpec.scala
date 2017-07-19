package io.finch.iteratee

import java.nio.charset.StandardCharsets

import com.twitter.io.Buf
import com.twitter.util.{Await, Future}
import io.catbird.util._
import io.finch.{Application, FinchSpec, Text, ToResponse}
import io.iteratee.Enumerator
import io.iteratee.twitter.FutureModule
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class ToResponseSpec extends FinchSpec with GeneratorDrivenPropertyChecks with FutureModule {

  "enumeratorToResponse" should "correctly encode Enumerator to Response" in {
    forAll { (data: List[Buf]) =>
      Await.result(
        enumeratorFromReader(response[Buf, Text.Plain](data).reader).toVector
      ) should contain theSameElementsAs data
    }
  }

  "enumeratorToJsonResponse" should "insert new lines after each chunk" in {
    forAll { (data: List[Buf]) =>
      Await.result(
        enumeratorFromReader(response[Buf, Application.Json](data).reader).toVector
      ) should contain theSameElementsAs data.map(_.concat(ToResponse.NewLine))
    }
  }

  private def response[A, CT <: String](data: List[A])(implicit tr: ToResponse.Aux[Enumerator[Future, A], CT]) = {
    val toResponse = implicitly[ToResponse.Aux[Enumerator[Future, A], CT]]
    val enumerator = enumList(data)

    toResponse(enumerator, StandardCharsets.UTF_8)
  }
}
