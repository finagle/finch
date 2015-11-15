package io.finch

import com.twitter.finagle.http.Request
import com.twitter.util.{Await, Throw, Try}
import io.finch.items._
import org.scalatest.{FlatSpec, Matchers}

class ApplicativeRequestReaderSpec extends FlatSpec with Matchers {

  val reader: RequestReader[(Int, Double, Int)] = (
    param("a").as[Int] ::
    param("b").as[Double] ::
    param("c").as[Int]
  ).asTuple
  
  def extractNotParsedTargets (result: Try[(Int, Double, Int)]): AnyRef = {
    (result handle {
      case Error.RequestErrors(errors) => errors map {
        case Error.NotParsed(item, _, _) => item
      }
      case Error.NotParsed(item, _, _) => Seq(item)
      case _ => Seq()
    }).get
  }

  "The applicative reader" should "produce three errors if all three numbers cannot be parsed" in {
    val request = Request("a"->"foo", "b"->"foo", "c"->"foo")
    extractNotParsedTargets(Await.result(reader(request).liftToTry)) shouldBe Seq(
      ParamItem("a"),
      ParamItem("b"),
      ParamItem("c")
    )
  }
  
  it should "produce two validation errors if two numbers cannot be parsed" in {
    val request = Request("a" -> "foo", "b" -> "7.7", "c" -> "foo")
    extractNotParsedTargets(Await.result(reader(request).liftToTry)) shouldBe Seq(
      ParamItem("a"),
      ParamItem("c")
    )
  }
  
  it should "produce two ParamNotFound errors if two parameters are missing" in {
    val request = Request("b" -> "7.7")
    Await.result(reader(request).liftToTry) shouldBe Throw(Error.RequestErrors(Seq(
      Error.NotPresent(ParamItem("a")),
      Error.NotPresent(ParamItem("c"))
    )))
  }

  it should "produce one error if the last parameter cannot be parsed to an integer" in {
    val request = Request("a"->"9", "b"->"7.7", "c"->"foo")
    extractNotParsedTargets(Await.result(reader(request).liftToTry)) shouldBe Seq(ParamItem("c"))
  }
  
  it should "parse all integers and doubles" in {
    val request = Request("a"->"9", "b"->"7.7", "c"->"5")
    Await.result(reader(request)) shouldBe ((9, 7.7, 5))
  }

  it should "compiles with both implicits Generic and DecodeRequest in the scope" in {
    case class MyString(s: String)
    implicit val decodeMyString: DecodeRequest[MyString] =
      DecodeRequest { s => Try(MyString(s)) }

    val foo: RequestReader[MyString] = param("a").as[MyString]
    Await.result(foo(Request("a" -> "foo"))) shouldBe MyString("foo")

    case class MyInt(i: Int)
    implicit val decodeMyInt: DecodeRequest[MyInt] =
      DecodeRequest { s => Try(MyInt(s.toInt)) }

    val bar: RequestReader[MyInt] = param("a").as[MyInt]
    Await.result(bar(Request("a" -> "100"))) shouldBe MyInt(100)
  }
}
