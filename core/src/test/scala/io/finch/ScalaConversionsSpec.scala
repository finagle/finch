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
}
