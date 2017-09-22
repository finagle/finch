package io.finch.syntax

import cats.Monad
import cats.syntax.functor._
import com.twitter.finagle.http.Response
import io.finch._
import io.finch.{FinchSpec, Text}
import org.scalacheck.Arbitrary
import org.scalatest.prop.GeneratorDrivenPropertyChecks

trait MapperSyntaxBehaviour extends FinchSpec with GeneratorDrivenPropertyChecks {

  implicit val arbResponse: Arbitrary[Response] = Arbitrary(genOutput[String].map(_.toResponse[Text.Plain]))

  def endpointMapper[F[_]](implicit ttf: ToTwitterFuture[F], monad: Monad[F]): Unit = {
    valueBehaviour(ttf, monad)
    function1behaviour(ttf, monad)
    function2behaviour(ttf, monad)
  }

  private def valueBehaviour[F[_]](implicit ttf: ToTwitterFuture[F], monad: Monad[F]): Unit = {
    it should "map Output value to endpoint" in {
      checkValue((i: String) => get(/) { Ok(i) })
    }

    it should "map Response value to endpoint" in {
      checkValue((i: Response) => get(/) { i })
    }

    it should "map F[Output[A]] value to endpoint" in {
      checkValue((i: String) => get(/) { Monad[F].pure(Ok(i)) })
    }

    it should "map F[Response] value to endpoint" in {
      checkValue((i: Response) => get(/) { Monad[F].pure(Ok(i).toResponse[Text.Plain]) })
    }

  }

  private def function1behaviour[F[_]](implicit ttf: ToTwitterFuture[F], monad: Monad[F]): Unit = {
    it should "map A => Output function to endpoint" in {
      checkFunction(get(int) { i: Int => Ok(i) })
    }

    it should "map A => Response function to endpoint" in {
      checkFunction(get(int) { i: Int => Ok(i).toResponse[Text.Plain] })
    }

    it should "map A => F[Output[A]] function to endpoint" in {
      checkFunction(get(int) { i: Int => Monad[F].pure(i).map(Ok) })
    }

    it should "map A => F[Response] function to endpoint" in {
      checkFunction(get(int) { i: Int => Monad[F].pure(i).map(Ok(_).toResponse[Text.Plain]) })
    }
  }

  private def function2behaviour[F[_]](implicit ttf: ToTwitterFuture[F], monad: Monad[F]): Unit = {
    it should "map (A, B) => Output function to endpoint" in {
      checkFunction2(get(string :: int) { (x: String, y: Int) => Ok(s"$x$y") })
    }

    it should "map (A, B) => Response function to endpoint" in {
      checkFunction2(get(string :: int) { (x: String, y: Int) => Ok(s"$x$y").toResponse[Text.Plain] })
    }

    it should "map (A, B) => F[Output[String]] function to endpoint" in {
      checkFunction2(get(string :: int) { (x: String, y: Int) => Monad[F].pure(Ok(s"$x$y")) })
    }

    it should "map (A, B) => F[Response] function to endpoint" in {
      checkFunction2(get(string :: int) { (x: String, y: Int) => Monad[F].pure(Ok(s"$x$y").toResponse[Text.Plain]) })
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
      }
    })
  }

  private def checkFunction2(e: Endpoint[_]): Unit = {
    forAll((x: String, y: Int) => {
      e(Input.get(s"/$x/$y")).awaitValueUnsafe() match {
        case Some(r: Response) => r.contentString shouldBe s"$x$y"
        case Some(a: String) => a shouldBe s"$x$y"

      }
    })
  }

}
