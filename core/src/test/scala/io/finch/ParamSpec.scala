package io.finch

import cats.Show
import cats.effect.SyncIO
import cats.syntax.all._
import io.finch.data.Foo

import java.util.UUID
import scala.reflect.ClassTag

class ParamSpec extends FinchSpec[SyncIO] {

  behavior of "param*"

  def laws[A: DecodeEntity: Show: ClassTag](k: String) =
    EntityEndpointLaws(paramOption[A](k), Dispatchers.forSyncIO)(v => Input.get("/", k -> v.show))

  checkAll("Param[String]", laws[String]("nickname").evaluating)
  checkAll("Param[Int]", laws[Int]("level").evaluating)
  checkAll("Param[Long]", laws[Long]("gold").evaluating)
  checkAll("Param[Boolean]", laws[Boolean]("hard-mode").evaluating)
  checkAll("Param[Float]", laws[Float]("multiplier").evaluating)
  checkAll("Param[Double]", laws[Double]("score").evaluating)
  checkAll("Param[UUID]", laws[UUID]("id").evaluating)
  checkAll("Param[Foo]", laws[Foo]("foo").evaluating)

  checkAll(
    "EvaluatingParam[String]",
    EvaluatingEndpointLaws[SyncIO, String](implicit de => param("foo")).all
  )

  it should "throw an error if required param is missing" in {
    val endpoint = param[UUID]("testEndpoint")
    an[Error.NotPresent] shouldBe thrownBy {
      endpoint(Input.get("/index")).value.unsafeRunSync()
    }
  }

  it should "throw an error if parameter is malformed" in {
    val endpoint = param[UUID]("testEndpoint")
    an[Error.NotParsed] shouldBe thrownBy {
      endpoint(Input.get("/index", "testEndpoint" -> "a")).value.unsafeRunSync()
    }
  }

  it should "collect errors on Endpoint[Seq[String]] failure" in {
    val endpoint = params[UUID]("testEndpoint")
    an[Errors] shouldBe thrownBy(
      endpoint(Input.get("/index", "testEndpoint" -> "a")).value.unsafeRunSync()
    )
  }

  it should "collect errors on Endpoint[NonEmptyList[String]] failure" in {
    val endpoint = paramsNel[UUID]("testEndpoint")
    an[Errors] shouldBe thrownBy(
      endpoint(Input.get("/index", "testEndpoint" -> "a")).value.unsafeRunSync()
    )
  }
}
