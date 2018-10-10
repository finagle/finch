package io.finch.iteratee

import java.nio.charset.StandardCharsets

import cats.effect.IO
import com.twitter.io.Buf
import io.finch.{Application, FinchSpec, Text, ToResponse}
import io.iteratee.Enumerator
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class ToResponseSpec extends FinchSpec with GeneratorDrivenPropertyChecks {

  behavior of "enumeratorToResponse"

  it should "correctly encode Enumerator to Response" in {
    forAll { data: List[Buf] =>
      enumeratorFromReader[IO](response[Buf, Text.Plain](data).reader).toVector.unsafeRunSync() should {
        contain theSameElementsAs data
      }
    }
  }

  it should "insert new lines after each chunk" in {
    forAll { data: List[Buf] =>
      enumeratorFromReader[IO](response[Buf, Application.Json](data).reader).toVector.unsafeRunSync() should {
        contain theSameElementsAs data.map(_.concat(ToResponse.NewLine))
      }
    }
  }

  private def response[A, CT <: String](data: List[A])(implicit tr: ToResponse.Aux[Enumerator[IO, A], CT]) = {
    val enumerator = Enumerator.enumList[IO, A](data)

    tr(enumerator, StandardCharsets.UTF_8)
  }
}
