package io.finch.petstore

import io.finch._
import io.finch.request._
import io.finch.request.items._

/**
 * Tells the API how to respond when certain exceptions are thrown.
 */
trait ErrorHandling {
  /**
   * Tells the service how to handle certain types of servable errors (i.e. PetstoreError)
   */
  def errorHandler: PartialFunction[Throwable, Output[Nothing]] = {
    case NotPresent(ParamItem(p)) => BadRequest(
      "error" -> "param_not_present", "param" -> p
    )
    case NotPresent(BodyItem) => BadRequest(
      "error" -> "body_not_present"
    )
    case NotParsed(ParamItem(p), _, _) => BadRequest(
      "error" -> "param_not_parsed", "param" -> p
    )
    case NotParsed(BodyItem, _, _) => BadRequest(
      "error" -> "body_not_parsed"
    )
    case NotValid(ParamItem(p), rule) => BadRequest(
      "error" -> "param_not_valid", "param" -> p, "rule" -> rule
    )
    // Domain errors
    case error: PetstoreError => NotFound(
      "error" -> error.message
    )
  }
}
