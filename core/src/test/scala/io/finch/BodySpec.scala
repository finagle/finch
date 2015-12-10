package io.finch

import com.twitter.finagle.http.Request
import com.twitter.util._

class BodySpec extends FinchSpec {

  "A body reader" should "read the optional HTTP body as a string" in {
    check { req: Request =>
      val cs = req.contentString
      val bo = Await.result(bodyOption(req))
      (cs.isEmpty && bo === None) || (cs.nonEmpty && bo === Some(cs))
    }
  }

  it should "read the required HTTP body as a string" in {
    check { req: Request =>
      val cs = req.contentString
      val b = Await.result(body(req).liftToTry)
      (cs.isEmpty && b === Throw(Error.NotPresent(items.BodyItem))) ||
      (cs.nonEmpty && b === Return(cs))
    }
  }

  it should "read the optional HTTP body as a byte array" in {
    check { req: Request =>
      val cb = req.contentString.getBytes("UTF-8")
      val bo = Await.result(binaryBodyOption(req))
      (cb.isEmpty && bo === None) || (cb.nonEmpty && bo.map(_.deep) === Some(cb.deep))
    }
  }

  it should "read the required HTTP body as a byte array" in {
    check { req: Request =>
      val cb = req.contentString.getBytes("UTF-8")
      val b = Await.result(binaryBody(req).liftToTry)
      (cb.isEmpty && b === Throw(Error.NotPresent(items.BodyItem))) ||
      (cb.nonEmpty && b.map(_.deep) === Return(cb.deep))
    }
  }

  it should "has a corresponding request item" in {
    body.item shouldBe items.BodyItem
    bodyOption.item shouldBe items.BodyItem
    binaryBody.item shouldBe items.BodyItem
    binaryBodyOption.item shouldBe items.BodyItem
  }
}
