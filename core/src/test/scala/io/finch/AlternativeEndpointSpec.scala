package io.finch

import cats.laws.discipline.AlternativeTests
import com.twitter.finagle.http.Request

class AlternativeEndpointSpec extends FinchSpec with MissingInstances {

  val input = Input(Request(""))
  implicit val eqEndpointString = eqEndpoint[String](input)
  implicit val eqEndpointStrings = eqEndpoint[(String, String, String)](input)

  checkAll("Endpoint[String]", AlternativeTests[Endpoint].applicative[String, String, String])

}
