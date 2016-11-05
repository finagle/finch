package io.finch

import scala.compat.Platform.EOL
import scala.reflect.ClassTag

import cats.data.NonEmptyList
import cats.laws.discipline.arbitrary._
import com.twitter.util.Try
import io.finch.Error.{Multiple, NotParsed, NotPresent, NotValid}
import io.finch.items.ParamItem

class ErrorSpec extends FinchSpec {

  behavior of "Error"

  it should "construct Error messages for all RequestErrors" in {
    check { (t: NonEmptyList[Throwable]) =>
      val sut = Multiple(t)
      val accResult = t.foldLeft("")((acc, b) => acc + EOL + "  " + Try(b.getMessage).getOrElse("Input was null"))
      sut.getMessage.contains(accResult)
    }
  }

  it should "construct an Error message for NotPresent" in {
    val input = ParamItem("ParamItemName")
    val sut = NotPresent(input)
    sut.getMessage shouldBe s"Required ${input.description} not present in the request."
  }

  it should "construct an Error message for NotValid" in {
    val input = ParamItem("ParamItemName")
    val rule = "Rule#1"
    val sut = NotValid(input, rule)
    sut.getMessage shouldBe s"Validation failed: ${input.description} $rule."
  }

  it should "construct an Error message for NotParsed" in {
    val input = ParamItem("ParamItemName")
    val cause = new Throwable("thrown")
    val targetType = ClassTag.Double
    val sut = NotParsed(input, targetType, cause)
    sut.getMessage shouldBe s"${input.description} cannot be converted to ${targetType.runtimeClass.getSimpleName}: " +
    s"${cause.getMessage}."
  }

  it should "return the cause for NotParsed" in {
    val input = ParamItem("ParamItemName")
    val cause = new Throwable("thrown")
    val targetType = ClassTag.Double
    val sut = NotParsed(input, targetType, cause)
    sut.getCause shouldBe cause
  }

  it should "construct an Error message for a Multiple" in {
    val sut = Multiple(NonEmptyList.of(Error("My Throwable")))
    sut.getMessage shouldBe
      """One or more errors reading request:
        |  My Throwable""".stripMargin
  }

  it should "construct an Error message from empty Error" in {
    val sut = Multiple(NonEmptyList.of(Error("")))
    sut.getMessage shouldBe
      """One or more errors reading request:
        |  """.stripMargin
  }

  it should "construct an Error message for a Multiple with multiple Throwables" in {
    val sut = Multiple(NonEmptyList.of(new Throwable("My Throwable"), Error("My Error")))
    sut.getMessage shouldBe
      """One or more errors reading request:
        |  My Throwable
        |  My Error""".stripMargin
  }

  it should "unapply existing RequestErrors matches" in {
    val t = new Throwable("My Throwable")
    val result = t match {
      case Error.RequestErrors(errors) => errors
      case rest => fail()
    }
    result shouldBe Seq(t)
  }

}
