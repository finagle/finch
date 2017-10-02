package io.finch

class ScalaConversionsSpec extends FinchSpec {
  behavior of "ScalaConversions"

  it should "convert Scala Futures into Twitter Futures" in {
    import scala.concurrent.{Future => ScalaFuture}
    import com.twitter.util.{Future=>TwitterFuture}
    import io.finch.internal.ScalaToTwitterConversions._
    import com.twitter.util.Await

    import scala.concurrent.ExecutionContext.Implicits.global

    val future: TwitterFuture[String] = ScalaFuture {
      "value"
    }.asTwitterFuture

    val result = Await.result(future)

    result shouldBe "value"
  }

  it should "convert Scala Try into Twitter Try" in {
    import scala.util.{Try => ScalaTry}
    import com.twitter.util.{Try=>TwitterTry}
    import io.finch.internal.ScalaToTwitterConversions._

    val sucessfulTry: TwitterTry[String] =  ScalaTry { "value" }
    val failureTry: TwitterTry[String] =  ScalaTry { throw new RuntimeException }

    sucessfulTry.isReturn shouldBe true
    failureTry.isThrow shouldBe true
  }
}
