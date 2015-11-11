package io.finch

import com.twitter.finagle.http.Status
import io.finch.response.EncodeResponse

trait Outputs {

  // 1xx
  def Continue[A](message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.Continue)

  def Processing(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.Processing)

  // 2xx
  def Ok(): Output.Payload[Unit] = Ok(())
  def Ok[A](a: A): Output.Payload[A] = Output.Payload(a, Status.Ok)

  def Created(): Output.Payload[Unit] = Created(())
  def Created[A](a: A): Output.Payload[A] = Output.Payload(a, Status.Created)

  def Accepted(): Output.Payload[Unit] = Accepted(())
  def Accepted[A](a: A): Output.Payload[A] = Output.Payload(a, Status.Accepted)

  def NonAuthoritativeInformation(): Output.Payload[Unit] = NonAuthoritativeInformation(())
  def NonAuthoritativeInformation[A](a: A): Output.Payload[A] = Output.Payload(a, Status.NonAuthoritativeInformation)

  def NoContent(): Output.Payload[Unit] = NoContent(())
  def NoContent[A](a: A): Output.Payload[A] = Output.Payload(a, Status.NoContent)

  def ResetContent(): Output.Payload[Unit] = ResetContent(())
  def ResetContent[A](a: A): Output.Payload[A] = Output.Payload(a, Status.ResetContent)

  def PartialContent(): Output.Payload[Unit] = PartialContent(())
  def PartialContent[A](a: A): Output.Payload[A] = Output.Payload(a, Status.PartialContent)

  def MultiStatus(): Output.Payload[Unit] = MultiStatus(())
  def MultiStatus[A](a: A): Output.Payload[A] = Output.Payload(a, Status.MultiStatus)

  // 3xx
  def MultipleChoices(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.MultipleChoices)

  def MovedPermanently(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.MovedPermanently)

  def Found(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.Found)

  def SeeOther(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.SeeOther)

  def NotModified(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.NotModified)

  def UseProxy(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.UseProxy)

  def TemporaryRedirect(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.TemporaryRedirect)

  // 4xx
  def BadRequest(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.BadRequest)

  def Unauthorized(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.Unauthorized)

  def PaymentRequired(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.PaymentRequired)

  def Forbidden(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.Forbidden)

  def NotFound(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.NotFound)

  def MethodNotAllowed(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.MethodNotAllowed)

  def NotAcceptable(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.NotAcceptable)

  def ProxyAuthenticationRequired(message: (String, String)*)(
    implicit e: EncodeResponse[Map[String, String]]
  ): Output.Failure = Output.Failure(message.toMap, Status.ProxyAuthenticationRequired)

  def RequestTimeout(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.RequestTimeout)

  def Conflict(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.Conflict)

  def Gone(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.Gone)

  def LengthRequired(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.LengthRequired)

  def PreconditionFailed(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.PreconditionFailed)

  def RequestEntityTooLarge(message: (String, String)*)(
    implicit e: EncodeResponse[Map[String, String]]
  ): Output.Failure = Output.Failure(message.toMap, Status.RequestEntityTooLarge)

  def RequestUriTooLong(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.RequestURITooLong)

  def UnsupportedMediaType(message: (String, String)*)(
    implicit e: EncodeResponse[Map[String, String]]
  ): Output.Failure = Output.Failure(message.toMap, Status.UnsupportedMediaType)

  def RequestedRangeNotSatisfiable(message: (String, String)*)(
    implicit e: EncodeResponse[Map[String, String]]
    ): Output.Failure = Output.Failure(message.toMap, Status.RequestedRangeNotSatisfiable)

  def ExpectationFailed(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.ExpectationFailed)

  def UnprocessableEntity(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.UnprocessableEntity)

  def Locked(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.Locked)

  def FailedDependency(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.FailedDependency)

  def UnorderedCollection(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.UnorderedCollection)

  def UpgradeRequired(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.UpgradeRequired)

  def PreconditionRequired(message: (String, String)*)(
    implicit e: EncodeResponse[Map[String, String]]
  ): Output.Failure = Output.Failure(message.toMap, Status.PreconditionRequired)

  def TooManyRequests(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.TooManyRequests)

  // 5xx
  def InternalServerError(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.InternalServerError)

  def NotImplemented(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.NotImplemented)

  def ServiceUnavailable(message: (String, String)*)(implicit e: EncodeResponse[Map[String, String]]): Output.Failure =
    Output.Failure(message.toMap, Status.ServiceUnavailable)

  def HttpVersionNotSupported(message: (String, String)*)(
    implicit e: EncodeResponse[Map[String, String]]
  ): Output.Failure = Output.Failure(message.toMap, Status.HttpVersionNotSupported)
}
