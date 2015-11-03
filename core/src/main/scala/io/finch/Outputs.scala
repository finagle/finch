package io.finch

import com.twitter.finagle.http.Status

trait Outputs {
  private[this] val emptyMsg: Map[String, String] = Map.empty[String, String]

  // 1xx
  val Continue: Output.Failure = Output.Failure(emptyMsg, Status.Continue)                                        // 100
  val Processing: Output.Failure = Output.Failure(emptyMsg, Status.Processing)                                    // 102

  // 2xx
  val Ok: Output.Payload[Unit] = Output.Payload((), Status.Ok)                                                    // 200
  val Created: Output.Payload[Unit] = Output.Payload((), Status.Created)                                          // 201
  val Accepted: Output.Payload[Unit] = Output.Payload((), Status.Accepted)                                        // 202
  val NonAuthoritativeInformation: Output.Payload[Unit] = Output.Payload((),
      Status.NonAuthoritativeInformation)                                                                         // 203
  val NoContent: Output.Payload[Unit] = Output.Payload((), Status.NoContent)                                      // 204
  val ResetContent: Output.Payload[Unit] =  Output.Payload((), Status.ResetContent)                               // 205
  val PartialContent: Output.Payload[Unit] = Output.Payload((), Status.PartialContent)                            // 206
  val MultiStatus: Output.Payload[Unit] = Output.Payload((), Status.MultiStatus)                                  // 208

  // 3xx
  val MultipleChoices: Output.Failure = Output.Failure(emptyMsg, Status.MultipleChoices)                          // 300
  val MovedPermanently: Output.Failure = Output.Failure(emptyMsg, Status.MovedPermanently)                        // 301
  val Found: Output.Failure = Output.Failure(emptyMsg, Status.Found)                                              // 302
  val SeeOther: Output.Failure = Output.Failure(emptyMsg, Status.SeeOther)                                        // 303
  val NotModified: Output.Failure = Output.Failure(emptyMsg, Status.NotModified)                                  // 304
  val UseProxy: Output.Failure = Output.Failure(emptyMsg, Status.UseProxy)                                        // 305
  val TemporaryRedirect: Output.Failure = Output.Failure(emptyMsg, Status.TemporaryRedirect)                      // 307

  // 4xx
  val BadRequest: Output.Failure = Output.Failure(emptyMsg, Status.BadRequest)                                    // 400
  val Unauthorized: Output.Failure = Output.Failure(emptyMsg, Status.Unauthorized)                                // 401
  val PaymentRequired: Output.Failure = Output.Failure(emptyMsg, Status.PaymentRequired)                          // 402
  val Forbidden: Output.Failure = Output.Failure(emptyMsg, Status.Forbidden)                                      // 403
  val NotFound: Output.Failure = Output.Failure(emptyMsg, Status.NotFound)                                        // 404
  val MethodNotAllowed: Output.Failure = Output.Failure(emptyMsg, Status.MethodNotAllowed)                        // 405
  val NotAcceptable: Output.Failure = Output.Failure(emptyMsg, Status.NotAcceptable)                              // 406
  val ProxyAuthenticationRequired: Output.Failure = Output.Failure(emptyMsg, Status.ProxyAuthenticationRequired)  // 407
  val RequestTimeOut: Output.Failure = Output.Failure(emptyMsg, Status.RequestTimeout)                            // 408
  val Conflict: Output.Failure = Output.Failure(emptyMsg, Status.Conflict)                                        // 409
  val Gone: Output.Failure = Output.Failure(emptyMsg, Status.Gone)                                                // 410
  val LengthRequired: Output.Failure = Output.Failure(emptyMsg, Status.LengthRequired)                            // 411
  val PreconditionFailed: Output.Failure = Output.Failure(emptyMsg, Status.PreconditionFailed)                    // 412
  val RequestEntityTooLarge: Output.Failure = Output.Failure(emptyMsg, Status.RequestEntityTooLarge)              // 413
  val RequestUriTooLong: Output.Failure = Output.Failure(emptyMsg, Status.RequestURITooLong)                      // 414
  val UnsupportedMediaType: Output.Failure = Output.Failure(emptyMsg, Status.UnsupportedMediaType)                // 415
  val RequestedRangeNotSatisfiable: Output.Failure = Output.Failure(emptyMsg, Status.RequestedRangeNotSatisfiable)// 416
  val ExpectationFailed: Output.Failure = Output.Failure(emptyMsg, Status.ExpectationFailed)                      // 417
  val UnprocessableEntity: Output.Failure = Output.Failure(emptyMsg, Status.UnprocessableEntity)                  // 422
  val Locked: Output.Failure = Output.Failure(emptyMsg, Status.Locked)                                            // 423
  val FailedDependency: Output.Failure = Output.Failure(emptyMsg, Status.FailedDependency)                        // 424
  val UnorderedCollection: Output.Failure = Output.Failure(emptyMsg, Status.UnorderedCollection)                  // 425
  val UpgradeRequired: Output.Failure = Output.Failure(emptyMsg, Status.UpgradeRequired)                          // 426
  val PreconditionRequired: Output.Failure = Output.Failure(emptyMsg, Status(428))                                // 428
  val TooManyRequests: Output.Failure = Output.Failure(emptyMsg, Status(429))                                     // 429

  // 5xx
  val InternalServerError: Output.Failure = Output.Failure(emptyMsg, Status.InternalServerError)                  // 500
  val NotImplemented: Output.Failure = Output.Failure(emptyMsg, Status.NotImplemented)                            // 501
  val BadGateway: Output.Failure = Output.Failure(emptyMsg, Status.BadGateway)                                    // 502
  val ServiceUnavailable: Output.Failure = Output.Failure(emptyMsg, Status.ServiceUnavailable)                    // 503
  val GatewayTimeout: Output.Failure = Output.Failure(emptyMsg, Status.GatewayTimeout)                            // 504
  val HttpVersionNotSupported: Output.Failure = Output.Failure(emptyMsg, Status.HttpVersionNotSupported)          // 505
  val VariantAlsoNegotiates: Output.Failure = Output.Failure(emptyMsg, Status.VariantAlsoNegotiates)              // 506
  val InsufficientStorage: Output.Failure = Output.Failure(emptyMsg, Status.InsufficientStorage)                  // 507
  val NotExtended: Output.Failure = Output.Failure(emptyMsg, Status.NotExtended)                                  // 510
}
