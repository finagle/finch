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
    case class Foo(s: String)
    implicit val decodeFoo: DecodeRequest[Foo] =
      DecodeRequest.instance(s => Try(Foo(s)))

    val foo: RequestReader[Foo] = param("a").as[Foo]
    Await.result(foo(Request("a" -> "foo"))) shouldBe Foo("foo")

    case class Bar(s: String)
    val bar: RequestReader[Bar] = param("a").as[Bar]
    Await.result(bar(Request("a" -> "100"))) shouldBe Bar("100")
  }
}
