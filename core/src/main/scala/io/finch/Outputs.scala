package io.finch

import com.twitter.finagle.httpx.{Response, Status}
import com.twitter.util.Future
import io.finch.response.EncodeResponse

trait Outputs extends LowPriorityOutputs  {
  import Endpoint.Output

  // Implicitly converts an `Endpoint.Output` to `Response`.
  implicit def outputToResponse[A](o: Endpoint.Output[A])(implicit e: EncodeResponse[A]): Response = {
    val rep = Response()
    // properties from `EncodeResponse`
    rep.content = e(o.value)
    rep.contentType = e.contentType
    e.charset.foreach { cs => rep.charset = cs }

    // properties from Output
    rep.status = o.status
    o.headers.foreach { case (k, v) => rep.headerMap.add(k, v) }
    o.cookies.foreach {
      rep.addCookie
    }
    o.contentType.foreach { ct => rep.contentType = ct }
    o.charset.foreach { cs => rep.charset = cs }

    rep
  }

  // Implicitly converts an `Endpoint.Output[Future[A]] to `Future[Endpoint.Output[A]]`.
  implicit def outputFutureToFutureOutput[A](o: Endpoint.Output[Future[A]]): Future[Endpoint.Output[A]] =
    o.value.map { value =>
      o.copy(value = value)
    }

  // 1xx
  val Continue: Output[Unit] = Output((), Status.Continue)                                          // 100
  val Processing: Output[Unit] = Output((), Status.Processing)                                      // 102

  // 2xx
  val Ok: Output[Unit] = Output((), Status.Ok)                                                      // 200
  val Created: Output[Unit] = Output((), Status.Created)                                            // 201
  val Accepted: Output[Unit] = Output((), Status.Accepted)                                          // 202
  val NonAuthoritativeInformation: Output[Unit] = Output((), Status.NonAuthoritativeInformation)    // 203
  val NoContent: Output[Unit] = Output((), Status.NoContent)                                        // 204
  val ResetContent: Output[Unit] =  Output((), Status.ResetContent)                                 // 205
  val PartialContent: Output[Unit] = Output((), Status.PartialContent)                              // 206
  val MultiStatus: Output[Unit] = Output((), Status.MultiStatus)                                    // 208

  // 3xx
  val MultipleChoices: Output[Unit] = Output((), Status.MultipleChoices)                            // 300
  val MovedPermanently: Output[Unit] = Output((), Status.MovedPermanently)                          // 301
  val Found: Output[Unit] = Output((), Status.Found)                                                // 302
  val SeeOther: Output[Unit] = Output((), Status.SeeOther)                                          // 303
  val NotModified: Output[Unit] = Output((), Status.NotModified)                                    // 304
  val UseProxy: Output[Unit] = Output((), Status.UseProxy)                                          // 305
  val TemporaryRedirect: Output[Unit] = Output((), Status.TemporaryRedirect)                        // 307

  // 4xx
  val BadRequest: Output[Unit] = Output((), Status.BadRequest)                                      // 400
  val Unauthorized: Output[Unit] = Output((), Status.Unauthorized)                                  // 401
  val PaymentRequired: Output[Unit] = Output((), Status.PaymentRequired)                            // 402
  val Forbidden: Output[Unit] = Output((), Status.Forbidden)                                        // 403
  val NotFound: Output[Unit] = Output((), Status.NotFound)                                          // 404
  val MethodNotAllowed: Output[Unit] = Output((), Status.MethodNotAllowed)                          // 405
  val NotAcceptable: Output[Unit] = Output((), Status.NotAcceptable)                                // 406
  val ProxyAuthenticationRequired: Output[Unit] = Output((), Status.ProxyAuthenticationRequired)    // 407
  val RequestTimeOut: Output[Unit] = Output((), Status.RequestTimeout)                              // 408
  val Conflict: Output[Unit] = Output((), Status.Conflict)                                          // 409
  val Gone: Output[Unit] = Output((), Status.Gone)                                                  // 410
  val LengthRequired: Output[Unit] = Output((), Status.LengthRequired)                              // 411
  val PreconditionFailed: Output[Unit] = Output((), Status.PreconditionFailed)                      // 412
  val RequestEntityTooLarge: Output[Unit] = Output((), Status.RequestEntityTooLarge)                // 413
  val RequestUriTooLong: Output[Unit] = Output((), Status.RequestURITooLong)                        // 414
  val UnsupportedMediaType: Output[Unit] = Output((), Status.UnsupportedMediaType)                  // 415
  val RequestedRangeNotSatisfiable: Output[Unit] = Output((), Status.RequestedRangeNotSatisfiable)  // 416
  val ExpectationFailed: Output[Unit] = Output((), Status.ExpectationFailed)                        // 417
  val UnprocessableEntity: Output[Unit] = Output((), Status.UnprocessableEntity)                    // 422
  val Locked: Output[Unit] = Output((), Status.Locked)                                              // 423
  val FailedDependency: Output[Unit] = Output((), Status.FailedDependency)                          // 424
  val UnorderedCollection: Output[Unit] = Output((), Status.UnorderedCollection)                    // 425
  val UpgradeRequired: Output[Unit] = Output((), Status.UpgradeRequired)                            // 426
  val PreconditionRequired: Output[Unit] = Output((), Status(428))                                  // 428
  val TooManyRequests: Output[Unit] = Output((), Status(429))                                       // 429

  // 5xx
  val InternalServerError: Output[Unit] = Output((), Status.InternalServerError)                    // 500
  val NotImplemented: Output[Unit] = Output((), Status.NotImplemented)                              // 501
  val BadGateway: Output[Unit] = Output((), Status.BadGateway)                                      // 502
  val ServiceUnavailable: Output[Unit] = Output((), Status.ServiceUnavailable)                      // 503
  val GatewayTimeout: Output[Unit] = Output((), Status.GatewayTimeout)                              // 504
  val HttpVersionNotSupported: Output[Unit] = Output((), Status.HttpVersionNotSupported)            // 505
  val VariantAlsoNegotiates: Output[Unit] = Output((), Status.VariantAlsoNegotiates)                // 506
  val InsufficientStorage: Output[Unit] = Output((), Status.InsufficientStorage)                    // 507
  val NotExtended: Output[Unit] = Output((), Status.NotExtended)                                    // 510
}
