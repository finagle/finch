package io.finch

import java.util.UUID

import com.twitter.io.Buf

class BodySpec extends FinchSpec {

  behavior of "body*"

  def withBody(b: String): Input = Input.post("/").withBody[Text.Plain](Buf.Utf8(b))

  checkAll("Body[String]", EntityEndpointLaws[String](bodyStringOption)(withBody).evaluating)
  checkAll("Body[Int]", EntityEndpointLaws[Int](bodyStringOption)(withBody).evaluating)
  checkAll("Body[Long]", EntityEndpointLaws[Long](bodyStringOption)(withBody).evaluating)
  checkAll("Body[Boolean]", EntityEndpointLaws[Boolean](bodyStringOption)(withBody).evaluating)
  checkAll("Body[Float]", EntityEndpointLaws[Float](bodyStringOption)(withBody).evaluating)
  checkAll("Body[Double]", EntityEndpointLaws[Double](bodyStringOption)(withBody).evaluating)
  checkAll("Body[UUID]", EntityEndpointLaws[UUID](bodyStringOption)(withBody).evaluating)
}
