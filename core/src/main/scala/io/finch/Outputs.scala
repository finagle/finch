package io.finch

import com.twitter.finagle.httpx.Status

trait Outputs {
  import Endpoint.Output

  trait OutputBuilder { self: Output[Unit] =>
    def apply[A](a: A): Output[A] = Output(a, self.status)
  }

  // 1xx
  object Continue extends Output((), Status.Continue) with OutputBuilder                       // 100
  object Processing extends Output((), Status.Processing) with OutputBuilder                   // 102

  // 2xx
  object Ok extends Output((), Status.Ok) with OutputBuilder                                   // 200
  object Created extends Output((), Status.Created) with OutputBuilder                         // 201
  object Accepted extends Output((), Status.Accepted) with OutputBuilder                       // 202
  object NonAuthoritativeInformation
    extends Output((), Status.NonAuthoritativeInformation) with OutputBuilder                  // 203
  object NoContent extends Output((), Status.NoContent) with OutputBuilder                     // 204
  object ResetContent extends Output((), Status.ResetContent) with OutputBuilder               // 205
  object PartialContent extends Output((), Status.PartialContent) with OutputBuilder           // 206
  object MultiStatus extends Output((), Status.MultiStatus) with OutputBuilder                 // 208

  // 3xx
  object MultipleChoices extends Output((), Status.MultipleChoices) with OutputBuilder         // 300
  object MovedPermanently extends Output((), Status.MovedPermanently) with OutputBuilder       // 301
  object Found extends Output((), Status.Found) with OutputBuilder                             // 302
  object SeeOther extends Output((), Status.SeeOther) with OutputBuilder                       // 303
  object NotModified extends Output((), Status.NotModified) with OutputBuilder                 // 304
  object UseProxy extends Output((), Status.UseProxy) with OutputBuilder                       // 305
  object TemporaryRedirect extends Output((), Status.TemporaryRedirect) with OutputBuilder     // 307

  // 4xx
  object BadRequest extends Output((), Status.BadRequest) with OutputBuilder                   // 400
  object Unauthorized extends Output((), Status.Unauthorized) with OutputBuilder               // 401
  object PaymentRequired extends Output((), Status.PaymentRequired) with OutputBuilder         // 402
  object Forbidden extends Output((), Status.Forbidden) with OutputBuilder                     // 403
  object NotFound extends Output((), Status.NotFound) with OutputBuilder                       // 404
  object MethodNotAllowed extends Output((), Status.MethodNotAllowed) with OutputBuilder       // 405
  object NotAcceptable extends Output((), Status.NotAcceptable) with OutputBuilder             // 406
  object ProxyAuthenticationRequired
    extends Output((), Status.ProxyAuthenticationRequired) with OutputBuilder                  // 407
  object RequestTimeOut extends Output((), Status.RequestTimeout) with OutputBuilder           // 408
  object Conflict extends Output((), Status.Conflict) with OutputBuilder                       // 409
  object Gone extends Output((), Status.Gone) with OutputBuilder                               // 410
  object LengthRequired extends Output((), Status.LengthRequired) with OutputBuilder           // 411
  object PreconditionFailed extends Output((), Status.PreconditionFailed) with OutputBuilder   // 412
  object RequestEntityTooLarge
    extends Output((), Status.RequestEntityTooLarge) with OutputBuilder                        // 413
  object RequestUriTooLong extends Output((), Status.RequestURITooLong) with OutputBuilder     // 414
  object UnsupportedMediaType
    extends Output((), Status.UnsupportedMediaType) with OutputBuilder                         // 415
  object RequestedRangeNotSatisfiable
    extends Output((), Status.RequestedRangeNotSatisfiable) with OutputBuilder                 // 416
  object ExpectationFailed extends Output((), Status.ExpectationFailed) with OutputBuilder     // 417
  object UnprocessableEntity extends Output((), Status.UnprocessableEntity) with OutputBuilder // 422
  object Locked extends Output((), Status.Locked) with OutputBuilder                           // 423
  object FailedDependency extends Output((), Status.FailedDependency) with OutputBuilder       // 424
  object UnorderedCollection extends Output((), Status.UnorderedCollection) with OutputBuilder // 425
  object UpgradeRequired extends Output((), Status.UpgradeRequired) with OutputBuilder         // 426
  object PreconditionRequired extends Output((), Status(428)) with OutputBuilder               // 428
  object TooManyRequests extends Output((), Status(429)) with OutputBuilder                    // 429

  // 5xx
  object InternalServerError extends Output((), Status.InternalServerError) with OutputBuilder // 500
  object NotImplemented extends Output((), Status.NotImplemented) with OutputBuilder           // 501
  object BadGateway extends Output((), Status.BadGateway) with OutputBuilder                   // 502
  object ServiceUnavailable extends Output((), Status.ServiceUnavailable) with OutputBuilder   // 503
  object GatewayTimeout extends Output((), Status.GatewayTimeout) with OutputBuilder           // 504
  object HttpVersionNotSupported
    extends Output((), Status.HttpVersionNotSupported) with OutputBuilder                      // 505
  object VariantAlsoNegotiates
    extends Output((), Status.VariantAlsoNegotiates) with OutputBuilder                        // 506
  object InsufficientStorage extends Output((), Status.InsufficientStorage) with OutputBuilder // 507
  object NotExtended extends Output((), Status.NotExtended) with OutputBuilder                 // 510
}
