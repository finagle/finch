package io.finch

import java.util.UUID

import com.twitter.io.Buf

class BodySpec extends FinchSpec {

  behavior of "param*"

  def withBody(b: String): Input = Input.post("/").withBody(Buf.Utf8(b))

  checkAll("Body[String]", EndpointLaws[String](bodyOption)(withBody).evaluating)
  checkAll("Body[Int]", EndpointLaws[Int](bodyOption)(withBody).evaluating)
  checkAll("Body[Long]", EndpointLaws[Long](bodyOption)(withBody).evaluating)
  checkAll("Body[Boolean]", EndpointLaws[Boolean](bodyOption)(withBody).evaluating)
  checkAll("Body[Float]", EndpointLaws[Float](bodyOption)(withBody).evaluating)
  checkAll("Body[Double]", EndpointLaws[Double](bodyOption)(withBody).evaluating)
  checkAll("Body[UUID]", EndpointLaws[UUID](bodyOption)(withBody).evaluating)
}
