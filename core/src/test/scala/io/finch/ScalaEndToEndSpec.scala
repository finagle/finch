package io.finch

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.Await

class ScalaEndToEndSpec extends FinchSpec {
  behavior of "Finch"

  it should "convert value Endpoints into Services, using Scala Futures" in {
    import scala.concurrent.{Future => ScalaFuture}
    import scala.concurrent.ExecutionContext.Implicits._
    import io.finch.syntax.scala._

    val e: Endpoint[String] = get("foo") {
      ScalaFuture {
        Ok("bar")
      }
    }

    val s: Service[Request, Response] = e.toServiceAs[Text.Plain]

    val rep = Await.result(s(Request("/foo")))
    rep.contentString shouldBe "bar"
    rep.status shouldBe Status.Ok
  }

  it should "convert value parameterized Endpoints into Services, using Scala Futures" in {
    import scala.concurrent.{Future => ScalaFuture}
    import scala.concurrent.ExecutionContext.Implicits._
    import io.finch.syntax.scala._

    val e: Endpoint[String] = get("foo" :: string) { param: String =>
      ScalaFuture {
        Ok(param)
      }
    }

    val s: Service[Request, Response] = e.toServiceAs[Text.Plain]

    val rep = Await.result(s(Request("/foo/bar")))
    rep.contentString shouldBe "bar"
    rep.status shouldBe Status.Ok
  }
}
