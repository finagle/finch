package io.finch.syntax

import cats.Monad
import cats.effect.Effect
import cats.syntax.functor._
import com.twitter.finagle.http.Response
import io.finch._
import org.scalacheck.Arbitrary
import org.scalatest.prop.GeneratorDrivenPropertyChecks

abstract class MapperSyntaxSpec[F[_]](endpoints: Module[F]) extends FinchSpec
  with GeneratorDrivenPropertyChecks {

  import endpoints._

  implicit val arbResponse: Arbitrary[Response] = Arbitrary(genOutput[String].map(_.toResponse[Text.Plain]))

  def endpointMapper(): Unit = {
    valueBehaviour()
    function1behaviour()
    function2behaviour()
  }

  private def valueBehaviour(): Unit = {
    it should "map Output value to endpoint" in {
      checkValue((i: String) => Endpoint[F].get(/) { Ok(i) })
    }

    it should "map Response value to endpoint" in {
      checkValue((i: Response) => Endpoint[F].get(/) { i })
    }

    it should "map F[Output[A]] value to endpoint" in {
      checkValue((i: String) => Endpoint[F].get(/) { Effect[F].pure(Ok(i)) })
    }

    it should "map F[Response] value to endpoint" in {
      checkValue((i: Response) => Endpoint[F].get(/) { Effect[F].pure(Ok(i).toResponse[Text.Plain]) })
    }

  }

  private def function1behaviour(): Unit = {
    it should "map A => Output function to endpoint" in {
      checkFunction(Endpoint[F].get(path[Int]) { i: Int => Ok(i) })
    }

    it should "map A => Response function to endpoint" in {
      checkFunction(Endpoint[F].get(path[Int]) { i: Int => Ok(i).toResponse[Text.Plain] })
    }

    it should "map A => F[Output[A]] function to endpoint" in {
      checkFunction(Endpoint[F].get(path[Int]) { i: Int => Effect[F].pure(i).map(Ok) })
    }

    implicitly[Effect[F]]
    it should "map A => F[Response] function to endpoint" in {
      checkFunction(Endpoint[F].get(path[Int]) { i: Int => Effect[F].pure(i).map(Ok(_).toResponse[Text.Plain]) })
    }
  }

  private def function2behaviour(): Unit = {
    it should "map (A, B) => Output function to endpoint" in {
      checkFunction2(Endpoint[F].get(path[Int] :: path[Int]) { (x: Int, y: Int) => Ok(s"$x$y") })
    }

    it should "map (A, B) => Response function to endpoint" in {
      checkFunction2(Endpoint[F].get(path[Int] :: path[Int]) { (x: Int, y: Int) => Ok(s"$x$y").toResponse[Text.Plain] })
    }

    it should "map (A, B) => F[Output[String]] function to endpoint" in {
      checkFunction2(Endpoint[F].get(path[Int] :: path[Int]) { (x: Int, y: Int) => Effect[F].pure(Ok(s"$x$y")) })
    }

    it should "map (A, B) => F[Response] function to endpoint" in {
      checkFunction2(Endpoint[F].get(path[Int] :: path[Int]) { (x: Int, y: Int) =>
        Monad[F].pure(Ok(s"$x$y").toResponse[Text.Plain]) })
    }
  }

  private def checkValue[A : Arbitrary](f: A => Endpoint[A]): Unit = {
    forAll((input: A) => {
      val e = f(input)
      e(Input.get("/")).awaitValueUnsafe() shouldBe Some(input)
    })
  }

  private def checkFunction(e: Endpoint[_]): Unit = {
    forAll((input: Int) => {
      e(Input.get(s"/$input")).awaitValueUnsafe() match {
        case Some(r: Response) => r.contentString shouldBe input.toString
        case Some(a: Int) => a shouldBe input
        case _ => ()
      }
    })
  }

  private def checkFunction2(e: Endpoint[_]): Unit = {
    forAll((x: Int, y: Int) => {
      e(Input.get(s"/$x/$y")).awaitValueUnsafe() match {
        case Some(r: Response) => r.contentString shouldBe s"$x$y"
        case Some(a: String) => a shouldBe s"$x$y"
        case _ => ()
      }
    })
  }

}
