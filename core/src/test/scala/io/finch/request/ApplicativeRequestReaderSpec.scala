package io.finch.request

import org.scalatest.{FlatSpec, Matchers}

import com.twitter.finagle.httpx.Request
import com.twitter.util.{Await, Throw, Try}
import items._

class ApplicativeRequestReaderSpec extends FlatSpec with Matchers {

  case class MyReq(http: Request, i: Int)
  implicit val reqEv: MyReq %> Request = View(_.http)

  val reader: RequestReader[(Int, Double, Int)] = (
    param("a").as[Int] ::
    param("b").as[Double] ::
    param("c").as[Int]
  ).asTuple
  
  def extractNotParsedTargets (result: Try[(Int, Double, Int)]): AnyRef = {
    (result handle {
      case RequestErrors(errors) => errors map {
        case NotParsed(item, _, _) => item
      }
      case NotParsed(item, _, _) => Seq(item)
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
    Await.result(reader(request).liftToTry) shouldBe Throw(RequestErrors(Seq(
      NotPresent(ParamItem("a")),
      NotPresent(ParamItem("c"))
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

  it should "be polymorphic in terms of request type" in {
    val i: PRequestReader[MyReq, Int] = RequestReader(_.i)
    val a = (i :: param("a")) ~> ((_: Int) + (_: String))
    val b = for {
      ii <- i
      aa <- param("a")
    } yield aa + ii

    Await.result(a(MyReq(Request("a" -> "foo"), 10))) shouldBe "10foo"
    Await.result(b(MyReq(Request("a" -> "foo"), 10))) shouldBe "foo10"
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
