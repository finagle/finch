package io.finch

import cats.effect.SyncIO
import cats.syntax.all._
import cats.{Eq, Show}
import io.finch.data.Foo
import org.scalacheck.Arbitrary

import java.util.UUID
import scala.reflect.ClassTag

class HeaderSpec extends FinchSpec[SyncIO] {

  behavior of "header*"

  def laws[A: DecodeEntity: Show: ClassTag](k: String) =
    EntityEndpointLaws(headerOption[A](k), Dispatchers.forSyncIO)(v => Input.get("/").withHeaders(k -> v.show))

  checkAll("Header[String]", laws[String]("nickname").evaluating(Arbitrary(genNonEmptyString), Eq[String]))
  checkAll("Header[Int]", laws[Int]("level").evaluating)
  checkAll("Header[Long]", laws[Long]("gold").evaluating)
  checkAll("Header[Boolean]", laws[Boolean]("hard-mode").evaluating)
  checkAll("Header[Float]", laws[Float]("multiplier").evaluating)
  checkAll("Header[Double]", laws[Double]("score").evaluating)
  checkAll("Header[UUID]", laws[UUID]("id").evaluating)
  checkAll("Header[Foo]", laws[Foo]("foo").evaluating)

  checkAll(
    "EvaluatingHeader[String]",
    EvaluatingEndpointLaws[SyncIO, String](implicit de => header("foo")).all
  )

  it should "throw an error if required header is missing" in {
    val endpoint = header[UUID]("header")
    an[Error.NotPresent] shouldBe thrownBy {
      endpoint(Input.get("/index")).value.unsafeRunSync()
    }
  }

  it should "throw an error if header is malformed" in {
    val endpoint = header[UUID]("header")
    an[Error.NotParsed] shouldBe thrownBy {
      endpoint(Input.get("/index").withHeaders("header" -> "a")).value.unsafeRunSync()
    }
  }
}
