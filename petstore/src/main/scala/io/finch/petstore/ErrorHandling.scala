package io.finch.petstore

import _root_.argonaut._, Argonaut._
import com.twitter.finagle.httpx.Response
import io.finch._
import io.finch.argonaut._
import io.finch.request._
import io.finch.request.items._

/**
 * Tells the API how to respond when certain exceptions are thrown.
 */
trait ErrorHandling {
  /**
   * Tells the service how to handle certain types of servable errors (i.e. PetstoreError)
   */
  def errorHandler: PartialFunction[Throwable, Response] = {
    case NotPresent(ParamItem(p)) => BadRequest(
      Map("error" -> "param_not_present", "param" -> p).asJson
    )
    case NotPresent(BodyItem) => BadRequest(
      Map("error" -> "body_not_present").asJson
    )
    case NotParsed(ParamItem(p), _, _) => BadRequest(
      Map("error" -> "param_not_parsed", "param" -> p).asJson
    )
    case NotParsed(BodyItem, _, _) => BadRequest(
      Map("error" -> "body_not_parsed").asJson
    )
    case NotValid(ParamItem(p), rule) => BadRequest(
      Map("error" -> "param_not_valid", "param" -> p, "rule" -> rule).asJson
    )
    // Domain errors
    case error: PetstoreError => NotFound(
      Map("error" -> error.message).asJson
    )
  }
}
