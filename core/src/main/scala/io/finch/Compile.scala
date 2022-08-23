package io.finch

import cats.syntax.all._
import cats.{Applicative, MonadThrow}
import com.twitter.finagle.http.{Method, Response, Status, Version}
import io.finch.internal.currentTime
import shapeless._

import scala.annotation.implicitNotFound

/** Compiles a given list of [[Endpoint]]s and their content-types into single [[Endpoint.Compiled]].
  *
  * Guarantees to:
  *
  *   - handle Finch's own errors (i.e., [[Error]] and [[Error]]) as 400s
  *   - copy requests's HTTP version onto a response
  *   - respond with 404 when an endpoint is not matched
  *   - respond with 405 when an endpoint is not matched because method wasn't allowed (serve back an `Allow` header)
  *   - include the date header on each response (unless disabled)
  *   - include the server header on each response (unless disabled)
  */
@implicitNotFound("""An Endpoint you're trying to compile is missing one or more encoders.

  Make sure each endpoint in ${ES}, ${CTS} is one of the following:

  * A com.twitter.finagle.http.Response
  * A value of a type with an io.finch.Encode instance (with the corresponding content-type)
  * A coproduct made up of some combination of the above

  See https://github.com/finagle/finch/blob/master/docs/src/main/tut/cookbook.md#fixing-the-toservice-compile-error
""")
trait Compile[F[_], ES <: HList, CTS <: HList] {
  def apply(endpoints: ES, options: Compile.Options, context: Compile.Context): Endpoint.Compiled[F]
}

object Compile {

  /** HTTP options propagated from [[Bootstrap]]. */
  final case class Options(
      includeDateHeader: Boolean,
      includeServerHeader: Boolean,
      enableMethodNotAllowed: Boolean,
      enableUnsupportedMediaType: Boolean,
      enableNotAcceptable: Boolean
  )

  /** HTTP context propagated between endpoints.
    *
    *   - `wouldAllow`: when non-empty, indicates that the incoming method wasn't allowed/matched
    */
  final case class Context(wouldAllow: List[Method] = Nil)

  private[this] val respond400: PartialFunction[Throwable, Output[Nothing]] = {
    case e: io.finch.Error   => Output.failure(e, Status.BadRequest)
    case es: io.finch.Errors => Output.failure(es, Status.BadRequest)
  }

  private[this] val respond415: PartialFunction[Throwable, Output[Nothing]] = {
    case e: io.finch.Error if e.getCause eq Decode.UnsupportedMediaTypeException =>
      Output.failure(e, Status.UnsupportedMediaType)
  }

  private def conformHttp(rep: Response, version: Version, opts: Options): Response = {
    rep.version = version
    if (opts.includeDateHeader) rep.headerMap.setUnsafe("Date", currentTime())
    if (opts.includeServerHeader) rep.headerMap.setUnsafe("Server", "Finch")
    rep
  }

  implicit def hnilTS[F[_]](implicit F: Applicative[F]): Compile[F, HNil, HNil] = (_, opts, ctx) =>
    Endpoint.Compiled { req =>
      val notAllowed = opts.enableMethodNotAllowed && ctx.wouldAllow.nonEmpty
      val rep = Response(if (notAllowed) Status.MethodNotAllowed else Status.NotFound)
      if (notAllowed) rep.allow = ctx.wouldAllow
      F.pure((Trace.empty, Right(conformHttp(rep, req.version, opts))))
    }

  implicit def hlistTS[F[_]: MonadThrow, A, ET <: HList, CTH, CTT <: HList](implicit
      negotiable: ToResponse.Negotiable[F, A, CTH],
      rest: Compile[F, ET, CTT],
      isNegotiable: CTH <:< Coproduct = null
  ): Compile[F, Endpoint[F, A] :: ET, CTH :: CTT] = { case (e :: es, opts, ctx) =>
    val endpoint = e.handle(if (opts.enableUnsupportedMediaType) respond415 orElse respond400 else respond400)
    Endpoint.Compiled { req =>
      endpoint(Input.fromRequest(req)) match {
        case EndpointResult.Matched(rem, trc, out) if rem.route.isEmpty =>
          val negotiate = isNegotiable != null || (opts.enableNotAcceptable && req.accept.nonEmpty)
          val negotiated = negotiable(if (negotiate) req.accept.map(Accept.fromString).toList else Nil)
          val acceptable = !negotiate || negotiated.acceptable || !opts.enableNotAcceptable
          val rep = if (acceptable) out.flatMap(_.toResponse(negotiated)) else Response(Status.NotAcceptable).pure[F]
          rep.map(conformHttp(_, req.version, opts)).attempt.map((trc, _))
        case EndpointResult.NotMatched.MethodNotAllowed(allowed) =>
          rest(es, opts, ctx.copy(wouldAllow = ctx.wouldAllow ++ allowed))(req)
        case _ =>
          rest(es, opts, ctx)(req)
      }
    }
  }
}
