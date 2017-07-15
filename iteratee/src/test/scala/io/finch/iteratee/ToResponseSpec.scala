package io.finch.iteratee

import java.nio.charset.StandardCharsets

import com.twitter.io.Buf
import com.twitter.util.{Await, Future}
import io.catbird.util._
import io.finch.{FinchSpec, Text, ToResponse}
import io.iteratee.Enumerator
import io.iteratee.twitter.FutureModule
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class ToResponseSpec extends FinchSpec with GeneratorDrivenPropertyChecks with FutureModule {

  "enumeratorToResponse" should "correctly encode Enumerator to Response" in {
    forAll { (data: List[Buf]) =>
      val toResponse: ToResponse.Aux[Enumerator[Future, Buf], Text.Plain] = implicitly
      val enumerator = enumList[Buf](data)

      val response = toResponse(enumerator, StandardCharsets.UTF_8)

      Await.result(enumeratorFromReader(response.reader).toVector) should contain theSameElementsAs data
    }
  }

}
