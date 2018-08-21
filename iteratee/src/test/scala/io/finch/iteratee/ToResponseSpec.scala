package io.finch.iteratee

import java.nio.charset.StandardCharsets

import com.twitter.io.Buf
import com.twitter.util.Await
import io.catbird.util._
import io.finch.{Application, FinchSpec, Text, ToResponse}
import io.finch.rerunnable.E
import io.iteratee.Enumerator
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class ToResponseSpec extends FinchSpec with GeneratorDrivenPropertyChecks {

  "enumeratorToResponse" should "correctly encode Enumerator to Response" in {
    forAll { (data: List[Buf]) =>
      Await.result(
        enumeratorFromReader(response[Buf, Text.Plain](data).reader).toVector.run
      ) should contain theSameElementsAs data
    }
  }

  "enumeratorToJsonResponse" should "insert new lines after each chunk" in {
    forAll { (data: List[Buf]) =>
      Await.result(
        enumeratorFromReader(response[Buf, Application.Json](data).reader).toVector.run
      ) should contain theSameElementsAs data.map(_.concat(ToResponse.NewLine))
    }
  }

  private def response[A, CT <: String](data: List[A])(implicit tr: ToResponse.Aux[Enumerator[Rerunnable, A], CT]) = {
    val enumerator = Enumerator.enumList[Rerunnable, A](data)

    tr(enumerator, StandardCharsets.UTF_8)
  }
}
