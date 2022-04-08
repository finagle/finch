package io.finch

import com.twitter.finagle.http.Status

trait Outputs {

  // See https://gist.github.com/vkostyukov/32c84c0c01789425c29a to understand how this list
  // is assembled.

  // 2xx
  def Ok[A](a: A): Output[A] = Output.payload(a, Status.Ok) // 200
  def Created[A](a: A): Output[A] = Output.payload(a, Status.Created) // 201
  def Accepted[A]: Output[A] = Output.empty(Status.Accepted) // 202
  def NoContent[A]: Output[A] = Output.empty(Status.NoContent) // 204

  // 4xx
  def BadRequest(cause: Exception): Output[Nothing] = Output.failure(cause, Status.BadRequest) // 400
  def Unauthorized(cause: Exception): Output[Nothing] =
    Output.failure(cause, Status.Unauthorized) // 401
  def PaymentRequired(cause: Exception): Output[Nothing] =
    Output.failure(cause, Status.PaymentRequired) // 402

  def Forbidden(cause: Exception): Output[Nothing] = Output.failure(cause, Status.Forbidden) // 403
  def NotFound(cause: Exception): Output[Nothing] = Output.failure(cause, Status.NotFound) // 404
  def MethodNotAllowed(cause: Exception): Output[Nothing] =
    Output.failure(cause, Status.MethodNotAllowed) // 405
  def NotAcceptable(cause: Exception): Output[Nothing] =
    Output.failure(cause, Status.NotAcceptable) // 406
  def RequestTimeout(cause: Exception): Output[Nothing] =
    Output.failure(cause, Status.RequestTimeout) // 408
  def Conflict(cause: Exception): Output[Nothing] = Output.failure(cause, Status.Conflict) // 409
  def Gone(cause: Exception): Output[Nothing] = Output.failure(cause, Status.Gone) // 410
  def LengthRequired(cause: Exception): Output[Nothing] =
    Output.failure(cause, Status.LengthRequired) // 411
  def PreconditionFailed(cause: Exception): Output[Nothing] =
    Output.failure(cause, Status.PreconditionFailed) // 412
  def RequestEntityTooLarge(cause: Exception): Output[Nothing] =
    Output.failure(cause, Status.RequestEntityTooLarge) // 413
  def RequestedRangeNotSatisfiable(cause: Exception): Output[Nothing] =
    Output.failure(cause, Status.RequestedRangeNotSatisfiable) // 416
  def EnhanceYourCalm(cause: Exception): Output[Nothing] =
    Output.failure(cause, Status.EnhanceYourCalm) // 420
  def UnprocessableEntity(cause: Exception): Output[Nothing] =
    Output.failure(cause, Status.UnprocessableEntity) // 422
  def TooManyRequests(cause: Exception): Output[Nothing] =
    Output.failure(cause, Status.TooManyRequests) // 429

  // 5xx
  def InternalServerError(cause: Exception): Output[Nothing] =
    Output.failure(cause, Status.InternalServerError) // 500
  def NotImplemented(cause: Exception): Output[Nothing] =
    Output.failure(cause, Status.NotImplemented) // 501
  def BadGateway(cause: Exception): Output[Nothing] =
    Output.failure(cause, Status.BadGateway) // 502
  def ServiceUnavailable(cause: Exception): Output[Nothing] =
    Output.failure(cause, Status.ServiceUnavailable) // 503
  def GatewayTimeout(cause: Exception): Output[Nothing] =
    Output.failure(cause, Status.GatewayTimeout) // 504
  def InsufficientStorage(cause: Exception): Output[Nothing] =
    Output.failure(cause, Status.InsufficientStorage) // 507
}
