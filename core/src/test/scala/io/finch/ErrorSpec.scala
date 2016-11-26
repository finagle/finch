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
    check { (t: NonEmptyList[Error]) =>
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
    val sut = Multiple(NonEmptyList.of(Error("My Custom Error"), Error("My Error")))
    sut.getMessage shouldBe
      """One or more errors reading request:
        |  My Custom Error
        |  My Error""".stripMargin
  }

  it should "unapply existing RequestErrors matches" in {
    val t = new Throwable("My Throwable")
    val ex = new Exception("My Exception")
    val e = Multiple(NonEmptyList.of(Error("a"), Error("b")))

    Error.RequestErrors.unapply(e) shouldBe Some(Seq(Error("a"), Error("b")))
    Error.RequestErrors.unapply(t) shouldBe None
    Error.RequestErrors.unapply(ex) shouldBe None
  }

  it should "equal anonymous Error classes with the same message" in {
    val error = Error("message")
    val sameerror = Error("message")
    val differenterror = Error("other message")
    error.equals(error) shouldBe true
    error.equals(sameerror) shouldBe true
    error.equals(differenterror) shouldBe false
  }

  it should "not equal other throwables" in {
    val error = Error("message")
    val ex = new Exception("message")

    error.equals(ex) shouldBe false
  }

  it should "have hashcode implemented correctly" in {
    check { (e: Error, ee: Error) =>
      val coll = collection.mutable.HashSet(e)
      coll.contains(e) && !coll.contains(ee)
    }
  }

}
