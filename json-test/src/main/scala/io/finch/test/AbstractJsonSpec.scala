package io.finch.test

import java.nio.charset.{Charset, StandardCharsets}

import scala.util.Try

import cats.instances.AllInstances
import cats.{Comonad, Eq, Functor}
import io.circe.Decoder
import io.finch.test.data._
import io.finch.{Decode, DecodeStream, Encode}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers
import org.typelevel.discipline.Laws

abstract class AbstractJsonSpec extends AnyFlatSpec with Matchers with Checkers with AllInstances {

  implicit val comonadEither: Comonad[Try] = new Comonad[Try] {
    def extract[A](x: Try[A]): A = x.get //never do it in production, kids

    def coflatMap[A, B](fa: Try[A])(f: Try[A] => B): Try[B] = Try(f(fa))

    def map[A, B](fa: Try[A])(f: A => B): Try[B] = fa.map(f)
  }

  implicit def arbitraryCharset: Arbitrary[Charset] = Arbitrary(
    Gen.oneOf(
      StandardCharsets.UTF_8,
      StandardCharsets.UTF_16,
      Charset.forName("UTF-32")
    )
  )

  implicit def arbitraryException: Arbitrary[Exception] = Arbitrary(
    Arbitrary.arbitrary[String].map(s => new Exception(s))
  )

  implicit def eqException: Eq[Exception] = Eq.instance((a, b) => a.getMessage == b.getMessage)

  implicit def decodeException: Decoder[Exception] = Decoder.forProduct1[Exception, String]("message")(s => new Exception(s))

  private def loop(name: String, ruleSet: Laws#RuleSet, library: String): Unit =
    for ((id, prop) <- ruleSet.all.properties) it should s"$library.$id.$name" in check(prop)

  def checkJson(library: String)(implicit
      e: Encode.Json[List[ExampleNestedCaseClass]],
      d: Decode.Json[List[ExampleNestedCaseClass]]
  ): Unit = {
    loop("List[ExampleNestedCaseClass]", JsonLaws.encoding[List[ExampleNestedCaseClass]].all, library)
    loop("List[ExampleNestedCaseClass]", JsonLaws.decoding[List[ExampleNestedCaseClass]].all, library)
  }

  def checkStreamJson[S[_[_], _], F[_]](library: String)(
      fromList: List[ExampleNestedCaseClass] => S[F, ExampleNestedCaseClass],
      toList: S[F, ExampleNestedCaseClass] => List[ExampleNestedCaseClass]
  )(implicit en: DecodeStream.Json[S, F, ExampleNestedCaseClass], functor: Functor[S[F, ?]]): Unit =
    loop(
      "ExampleNestedCaseClass",
      JsonLaws.streaming[S, F, ExampleNestedCaseClass](fromList, toList).all,
      library
    )
}
