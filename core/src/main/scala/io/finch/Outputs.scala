package io.finch

import com.twitter.finagle.http.Status

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
  def ToManyRequests(cause: Exception): Output.Failure = Output.Failure(cause, Status.TooManyRequests)             //429

  // 5xx
  def InternalServerError(cause: Exception): Output.Failure = Output.Failure(cause, Status.InternalServerError)    //500
  def NotImplemented(cause: Exception): Output.Failure = Output.Failure(cause, Status.NotImplemented)              //501
  def BadGateway(cause: Exception): Output.Failure = Output.Failure(cause, Status.BadGateway)                      //502
  def ServiceUnavailable(cause: Exception): Output.Failure = Output.Failure(cause, Status.ServiceUnavailable)      //503
  def GatewayTimeout(cause: Exception): Output.Failure = Output.Failure(cause, Status.GatewayTimeout)              //504
  def InsufficientStorage(cause: Exception): Output.Failure = Output.Failure(cause, Status.InsufficientStorage)    //507
}
