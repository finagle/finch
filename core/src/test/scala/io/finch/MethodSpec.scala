package io.finch

import cats.effect.IO
import com.twitter.finagle.http.Response
import org.scalacheck.Arbitrary
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class MethodSpec
  extends FinchSpec
  with GeneratorDrivenPropertyChecks {

  behavior of "method"

  implicit val arbResponse: Arbitrary[Response] = Arbitrary(genOutput[String].map(_.toResponse[Text.Plain]))

  it should "map Output value to endpoint" in {
    checkValue((i: String) => get(zero) { Ok(i) })
  }

  it should "map Response value to endpoint" in {
    checkValue((i: Response) => get(zero) { i })
  }

  it should "map F[Output[A]] value to endpoint" in {
    checkValue((i: String) => get(zero) { IO.pure(Ok(i)) })
  }

  it should "map F[Response] value to endpoint" in {
    checkValue((i: Response) => get(zero) { IO.pure(Ok(i).toResponse[Text.Plain]) })
  }

  it should "map A => Output function to endpoint" in {
    checkFunction(get(path[Int]) { i: Int => Ok(i) })
  }

  it should "map A => Response function to endpoint" in {
    checkFunction(get(path[Int]) { i: Int => Ok(i).toResponse[Text.Plain] })
  }

  it should "map A => F[Output[A]] function to endpoint" in {
    checkFunction(get(path[Int]) { i: Int => IO.pure(i).map(Ok) })
  }

  it should "map A => F[Response] function to endpoint" in {
    checkFunction(get(path[Int]) { i: Int => IO.pure(i).map(Ok(_).toResponse[Text.Plain]) })
  }

  it should "map (A, B) => Output function to endpoint" in {
    checkFunction2(get(path[Int] :: path[Int]) { (x: Int, y: Int) => Ok(s"$x$y") })
  }

  it should "map (A, B) => Response function to endpoint" in {
    checkFunction2(get(path[Int] :: path[Int]) { (x: Int, y: Int) => Ok(s"$x$y").toResponse[Text.Plain] })
  }

  it should "map (A, B) => F[Output[String]] function to endpoint" in {
    checkFunction2(get(path[Int] :: path[Int]) { (x: Int, y: Int) => IO.pure(Ok(s"$x$y")) })
  }

  it should "map (A, B) => F[Response] function to endpoint" in {
    checkFunction2(get(path[Int] :: path[Int]) { (x: Int, y: Int) =>
      IO.pure(Ok(s"$x$y").toResponse[Text.Plain]) })
  }

  private def checkValue[A : Arbitrary](f: A => Endpoint[IO, A]): Unit = {
    forAll((input: A) => {
      val e = f(input)
      e(Input.get("/")).awaitValueUnsafe() shouldBe Some(input)
    })
  }

  private def checkFunction(e: Endpoint[IO, _]): Unit = {
    forAll((input: Int) => {
      e(Input.get(s"/$input")).awaitValueUnsafe() match {
        case Some(r: Response) => r.contentString shouldBe input.toString
        case Some(a: Int) => a shouldBe input
        case _ => ()
      }
    })
  }

  private def checkFunction2(e: Endpoint[IO, _]): Unit = {
    forAll((x: Int, y: Int) => {
      e(Input.get(s"/$x/$y")).awaitValueUnsafe() match {
        case Some(r: Response) => r.contentString shouldBe s"$x$y"
        case Some(a: String) => a shouldBe s"$x$y"
        case _ => ()
      }
    })
  }
}
