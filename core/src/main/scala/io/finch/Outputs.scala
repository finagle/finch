package io.finch

import com.twitter.finagle.http.Status

trait Outputs {
  private[this] val emptyErr: Map[String, String] = Map.empty[String, String]

  // 1xx
  val Continue: Output.Payload[Unit] = Output.Payload((), Status.Continue)                                  // 100
  val Processing: Output.Payload[Unit] = Output.Payload((), Status.Processing)                              // 102

  // 2xx
  val Ok: Output.Payload[Unit] = Output.Payload((), Status.Ok)                                              // 200
  val Created: Output.Payload[Unit] = Output.Payload((), Status.Created)                                    // 201
  val Accepted: Output.Payload[Unit] = Output.Payload((), Status.Accepted)                                  // 202
  val NonAuthoritativeInformation: Output.Payload[Unit] = Output.Payload((),
      Status.NonAuthoritativeInformation)                                                                         // 203
  val NoContent: Output.Payload[Unit] = Output.Payload((), Status.NoContent)                                // 204
  val ResetContent: Output.Payload[Unit] =  Output.Payload((), Status.ResetContent)                         // 205
  val PartialContent: Output.Payload[Unit] = Output.Payload((), Status.PartialContent)                      // 206
  val MultiStatus: Output.Payload[Unit] = Output.Payload((), Status.MultiStatus)                            // 208

  // 3xx
  val MultipleChoices: Output.Error = Output.Error(emptyErr, Status.MultipleChoices)                          // 300
  val MovedPermanently: Output.Error = Output.Error(emptyErr, Status.MovedPermanently)                        // 301
  val Found: Output.Error = Output.Error(emptyErr, Status.Found)                                              // 302
  val SeeOther: Output.Error = Output.Error(emptyErr, Status.SeeOther)                                        // 303
  val NotModified: Output.Error = Output.Error(emptyErr, Status.NotModified)                                  // 304
  val UseProxy: Output.Error = Output.Error(emptyErr, Status.UseProxy)                                        // 305
  val TemporaryRedirect: Output.Error = Output.Error(emptyErr, Status.TemporaryRedirect)                      // 307

  // 4xx
  val BadRequest: Output.Error = Output.Error(emptyErr, Status.BadRequest)                                    // 400
  val Unauthorized: Output.Error = Output.Error(emptyErr, Status.Unauthorized)                                // 401
  val PaymentRequired: Output.Error = Output.Error(emptyErr, Status.PaymentRequired)                          // 402
  val Forbidden: Output.Error = Output.Error(emptyErr, Status.Forbidden)                                      // 403
  val NotFound: Output.Error = Output.Error(emptyErr, Status.NotFound)                                        // 404
  val MethodNotAllowed: Output.Error = Output.Error(emptyErr, Status.MethodNotAllowed)                        // 405
  val NotAcceptable: Output.Error = Output.Error(emptyErr, Status.NotAcceptable)                              // 406
  val ProxyAuthenticationRequired: Output.Error = Output.Error(emptyErr, Status.ProxyAuthenticationRequired)  // 407
  val RequestTimeOut: Output.Error = Output.Error(emptyErr, Status.RequestTimeout)                            // 408
  val Conflict: Output.Error = Output.Error(emptyErr, Status.Conflict)                                        // 409
  val Gone: Output.Error = Output.Error(emptyErr, Status.Gone)                                                // 410
  val LengthRequired: Output.Error = Output.Error(emptyErr, Status.LengthRequired)                            // 411
  val PreconditionFailed: Output.Error = Output.Error(emptyErr, Status.PreconditionFailed)                    // 412
  val RequestEntityTooLarge: Output.Error = Output.Error(emptyErr, Status.RequestEntityTooLarge)              // 413
  val RequestUriTooLong: Output.Error = Output.Error(emptyErr, Status.RequestURITooLong)                      // 414
  val UnsupportedMediaType: Output.Error = Output.Error(emptyErr, Status.UnsupportedMediaType)                // 415
  val RequestedRangeNotSatisfiable: Output.Error = Output.Error(emptyErr, Status.RequestedRangeNotSatisfiable)// 416
  val ExpectationFailed: Output.Error = Output.Error(emptyErr, Status.ExpectationFailed)                      // 417
  val UnprocessableEntity: Output.Error = Output.Error(emptyErr, Status.UnprocessableEntity)                  // 422
  val Locked: Output.Error = Output.Error(emptyErr, Status.Locked)                                            // 423
  val FailedDependency: Output.Error = Output.Error(emptyErr, Status.FailedDependency)                        // 424
  val UnorderedCollection: Output.Error = Output.Error(emptyErr, Status.UnorderedCollection)                  // 425
  val UpgradeRequired: Output.Error = Output.Error(emptyErr, Status.UpgradeRequired)                          // 426
  val PreconditionRequired: Output.Error = Output.Error(emptyErr, Status(428))                                // 428
  val TooManyRequests: Output.Error = Output.Error(emptyErr, Status(429))                                     // 429

  // 5xx
  val InternalServerError: Output.Error = Output.Error(emptyErr, Status.InternalServerError)                  // 500
  val NotImplemented: Output.Error = Output.Error(emptyErr, Status.NotImplemented)                            // 501
  val BadGateway: Output.Error = Output.Error(emptyErr, Status.BadGateway)                                    // 502
  val ServiceUnavailable: Output.Error = Output.Error(emptyErr, Status.ServiceUnavailable)                    // 503
  val GatewayTimeout: Output.Error = Output.Error(emptyErr, Status.GatewayTimeout)                            // 504
  val HttpVersionNotSupported: Output.Error = Output.Error(emptyErr, Status.HttpVersionNotSupported)          // 505
  val VariantAlsoNegotiates: Output.Error = Output.Error(emptyErr, Status.VariantAlsoNegotiates)              // 506
  val InsufficientStorage: Output.Error = Output.Error(emptyErr, Status.InsufficientStorage)                  // 507
  val NotExtended: Output.Error = Output.Error(emptyErr, Status.NotExtended)                                  // 510
}
