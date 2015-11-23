package io.finch

import com.twitter.finagle.http.Status
import io.finch.Error.MapException

trait Outputs {

  // See https://gist.github.com/vkostyukov/32c84c0c01789425c29a to understand how this list is assembled.

  // 2xx
  def Ok[A](a: A): Output.Payload[A] = Output.Payload(a, Status.Ok)                                               // 200
  def Created[A](a: A): Output.Payload[A] = Output.Payload(a, Status.Created)                                     // 201
  def Accepted[A](a: A): Output.Payload[A] = Output.Payload(a, Status.Accepted)                                   // 202
  def NoContent[A](a: A): Output.Payload[A] = Output.Payload(a, Status.NoContent)                                 // 204

  // 3xx
  def MovedPermanently(cause: Exception): Output.Failure = Output.Failure(cause, Status.MovedPermanently)          //301
  def Found(cause: Exception): Output.Failure = Output.Failure(cause, Status.Found)                                //302
  def SeeOther(cause: Exception): Output.Failure = Output.Failure(cause, Status.SeeOther)                          //303
  def NotModified(cause: Exception): Output.Failure = Output.Failure(cause, Status.NotModified)                    //304
  def TemporaryRedirect(cause: Exception): Output.Failure = Output.Failure(cause, Status.TemporaryRedirect)        //307
  def PermanentRedirect(cause: Exception): Output.Failure = Output.Failure(cause, Status(308))                     //308

  // 4xx
  def BadRequest(cause: Exception): Output.Failure = Output.Failure(cause, Status.BadRequest)                      //400
  def Unauthorized(cause: Exception): Output.Failure = Output.Failure(cause, Status.Unauthorized)                  //401
  def PaymentRequired(cause: Exception): Output.Failure = Output.Failure(cause, Status.PaymentRequired)            //402
  def Forbidden(cause: Exception): Output.Failure = Output.Failure(cause, Status.Forbidden)                        //403
  def NotFound(cause: Exception): Output.Failure = Output.Failure(cause, Status.NotFound)                          //404
  def MethodNotAllowed(cause: Exception): Output.Failure = Output.Failure(cause, Status.MethodNotAllowed)          //405
  def NotAcceptable(cause: Exception): Output.Failure = Output.Failure(cause, Status.NotAcceptable)                //406
  def RequestTimeout(cause: Exception): Output.Failure = Output.Failure(cause, Status.RequestTimeout)              //408
  def Conflict(cause: Exception): Output.Failure = Output.Failure(cause, Status.Conflict)                          //409
  def Gone(cause: Exception): Output.Failure = Output.Failure(cause, Status.Gone)                                  //410
  def LengthRequired(cause: Exception): Output.Failure = Output.Failure(cause, Status.LengthRequired)              //411
  def PreconditionFailed(cause: Exception): Output.Failure = Output.Failure(cause, Status.PreconditionFailed)      //412
  def RequestEntityTooLarge(cause: Exception): Output.Failure = Output.Failure(cause, Status.RequestEntityTooLarge)//413
  def RequestedRangeNotSatisfiable(cause: Exception): Output.Failure = Output.Failure(
      cause, Status.RequestedRangeNotSatisfiable)                                                                  //416
  def EnhanceYourCalm(cause: Exception): Output.Failure = Output.Failure(cause, Status.EnhanceYourCalm)            //420
  def UnprocessableEntity(cause: Exception): Output.Failure = Output.Failure(cause, Status.UnprocessableEntity)    //422
  def TooManyRequests(cause: Exception): Output.Failure = Output.Failure(cause, Status.TooManyRequests)            //429

  // 5xx
  def InternalServerError(cause: Exception): Output.Failure = Output.Failure(cause, Status.InternalServerError)    //500
  def NotImplemented(cause: Exception): Output.Failure = Output.Failure(cause, Status.NotImplemented)              //501
  def BadGateway(cause: Exception): Output.Failure = Output.Failure(cause, Status.BadGateway)                      //502
  def ServiceUnavailable(cause: Exception): Output.Failure = Output.Failure(cause, Status.ServiceUnavailable)      //503
  def GatewayTimeout(cause: Exception): Output.Failure = Output.Failure(cause, Status.GatewayTimeout)              //504
  def InsufficientStorage(cause: Exception): Output.Failure = Output.Failure(cause, Status.InsufficientStorage)    //507

  // ------ 0.9.1-compatible API (will be removed in 0.9.3) ------

  // 3xx
  @deprecated("Use MovedPermanently(Exception) instead", "0.9.2")
  def MovedPermanently(messages: (String, String)*): Output.Failure = MovedPermanently(MapException(messages.toMap))
  @deprecated("Use Found(Exception) instead", "0.9.2")
  def Found(messages: (String, String)*): Output.Failure = Found(MapException(messages.toMap))
  @deprecated("Use SeeOther(Exception) instead", "0.9.2")
  def SeeOther(messages: (String, String)*): Output.Failure = SeeOther(MapException(messages.toMap))
  @deprecated("Use NotModified(Exception) instead", "0.9.2")
  def NotModified(messages: (String, String)*): Output.Failure = NotModified(MapException(messages.toMap))
  @deprecated("Use TemporaryRedirect(Exception) instead", "0.9.2")
  def TemporaryRedirect(messages: (String, String)*): Output.Failure = TemporaryRedirect(MapException(messages.toMap))
  @deprecated("Use PermanentRedirect(Exception) instead", "0.9.2")
  def PermanentRedirect(messages: (String, String)*): Output.Failure = PermanentRedirect(MapException(messages.toMap))

  // 4xx
  @deprecated("Use BadRequest(Exception) instead", "0.9.2")
  def BadRequest(messages: (String, String)*): Output.Failure = BadRequest(MapException(messages.toMap))
  @deprecated("Use Unauthorized(Exception) instead", "0.9.2")
  def Unauthorized(messages: (String, String)*): Output.Failure = Unauthorized(MapException(messages.toMap))
  @deprecated("Use PaymentRequired(Exception) instead", "0.9.2")
  def PaymentRequired(messages: (String, String)*): Output.Failure = PaymentRequired(MapException(messages.toMap))
  @deprecated("Use Forbidden(Exception) instead", "0.9.2")
  def Forbidden(messages: (String, String)*): Output.Failure = Forbidden(MapException(messages.toMap))
  @deprecated("Use NotFound(Exception) instead", "0.9.2")
  def NotFound(messages: (String, String)*): Output.Failure = NotFound(MapException(messages.toMap))
  @deprecated("Use MethodNotAllowed(Exception) instead", "0.9.2")
  def MethodNotAllowed(messages: (String, String)*): Output.Failure = MethodNotAllowed(MapException(messages.toMap))
  @deprecated("Use NotAcceptable(Exception) instead", "0.9.2")
  def NotAcceptable(messages: (String, String)*): Output.Failure = NotAcceptable(MapException(messages.toMap))
  @deprecated("Use RequestTimeout(Exception) instead", "0.9.2")
  def RequestTimeout(messages: (String, String)*): Output.Failure = RequestTimeout(MapException(messages.toMap))
  @deprecated("Use Conflict(Exception) instead", "0.9.2")
  def Conflict(messages: (String, String)*): Output.Failure = Conflict(MapException(messages.toMap))
  @deprecated("Use Gone(Exception) instead", "0.9.2")
  def Gone(messages: (String, String)*): Output.Failure = Gone(MapException(messages.toMap))
  @deprecated("Use LengthRequired(Exception) instead", "0.9.2")
  def LengthRequired(messages: (String, String)*): Output.Failure = LengthRequired(MapException(messages.toMap))
  @deprecated("Use PreconditionFailed(Exception) instead", "0.9.2")
  def PreconditionFailed(messages: (String, String)*): Output.Failure = PreconditionFailed(MapException(messages.toMap))
  @deprecated("Use RequestEntityTooLarge(Exception) instead", "0.9.2")
  def RequestEntityTooLarge(messages: (String, String)*): Output.Failure =
    RequestEntityTooLarge(MapException(messages.toMap))
  @deprecated("Use RequestedRangeNotSatisfiable(Exception) instead", "0.9.2")
  def RequestedRangeNotSatisfiable(messages: (String, String)*): Output.Failure =
    RequestedRangeNotSatisfiable(MapException(messages.toMap))
  @deprecated("Use EnhanceYourCalm(Exception) instead", "0.9.2")
  def EnhanceYourCalm(messages: (String, String)*): Output.Failure = EnhanceYourCalm(MapException(messages.toMap))
  @deprecated("Use UnprocessableEntity(Exception) instead", "0.9.2")
  def UnprocessableEntity(messages: (String, String)*): Output.Failure =
    UnprocessableEntity(MapException(messages.toMap))
  @deprecated("Use TooManyRequests(Exception) instead", "0.9.2")
  def TooManyRequests(messages: (String, String)*): Output.Failure = TooManyRequests(MapException(messages.toMap))

  // 5xx
  @deprecated("Use InternalServerError(Exception) instead", "0.9.2")
  def InternalServerError(messages: (String, String)*): Output.Failure =
    InternalServerError(MapException(messages.toMap))
  @deprecated("Use NotImplemented(Exception) instead", "0.9.2")
  def NotImplemented(messages: (String, String)*): Output.Failure = NotImplemented(MapException(messages.toMap))
  @deprecated("Use BadGateway(Exception) instead", "0.9.2")
  def BadGateway(messages: (String, String)*): Output.Failure = BadGateway(MapException(messages.toMap))
  @deprecated("Use ServiceUnavailable(Exception) instead", "0.9.2")
  def ServiceUnavailable(messages: (String, String)*): Output.Failure = ServiceUnavailable(MapException(messages.toMap))
  @deprecated("Use GatewayTimeout(Exception) instead", "0.9.2")
  def GatewayTimeout(messages: (String, String)*): Output.Failure = GatewayTimeout(MapException(messages.toMap))
  @deprecated("Use InsufficientStorage(Exception) instead", "0.9.2")
  def InsufficientStorage(messages: (String, String)*): Output.Failure =
    InsufficientStorage(MapException(messages.toMap))
}
