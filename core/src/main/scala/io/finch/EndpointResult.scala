package io.finch

import scala.concurrent.duration.Duration

import cats.Id
import cats.effect.Effect
import com.twitter.finagle.http.Method
import com.twitter.util._

/**
  * A result returned from an [[Endpoint]]. This models `Option[(Input, Future[Output])]` and
  * represents two cases:
  *
  *  - Endpoint is matched (think of 200).
  *  - Endpoint is not matched (think of 404, 405, etc).
  *
  * In its current state, `EndpointResult.NotMatched` represented with two cases:
  *
  *  - `EndpointResult.NotMatched` (very generic result usually indicating 404)
  *  - `EndpointResult.NotMatched.MethodNotAllowed` (indicates 405)
  */
sealed abstract class EndpointResult[F[_], +A] {

  /**
    * Whether the [[Endpoint]] is matched on a given [[Input]].
    */
  final def isMatched: Boolean = this match {
    case EndpointResult.Matched(_, _, _) => true
    case _                               => false
  }

  /**
    * Returns the remainder of the [[Input]] after an [[Endpoint]] is matched.
    */
  final def remainder: Option[Input] = this match {
    case EndpointResult.Matched(rem, _, _) => Some(rem)
    case _                                 => None
  }

  /**
    * Returns the [[Trace]] if an [[Endpoint]] is matched.
    */
  final def trace: Option[Trace] = this match {
    case EndpointResult.Matched(_, trc, _) => Some(trc)
    case _                                 => None
  }

  def awaitOutput(d: Duration = Duration.Inf)(implicit F: Effect[F]): Option[Either[Throwable, Output[A]]] = this match {
    case EndpointResult.Matched(_, _, out) =>
      try F.toIO(out).unsafeRunTimed(d) match {
        case Some(a) => Some(Right(a))
        case _       => Some(Left(new TimeoutException(s"Output wasn't returned in time: $d")))
      } catch {
        case e: Throwable => Some(Left(e))
      }
    case _ => None
  }

  def awaitOutputUnsafe(d: Duration = Duration.Inf)(implicit F: Effect[F]): Option[Output[A]] =
    awaitOutput(d).map {
      case Right(r) => r
      case Left(ex) => throw ex
    }

  def awaitValue(d: Duration = Duration.Inf)(implicit F: Effect[F]): Option[Either[Throwable, A]] =
    awaitOutput(d).map {
      case Right(oa) => Right(oa.value)
      case Left(ob)  => Left(ob)
    }

  def awaitValueUnsafe(d: Duration = Duration.Inf)(implicit F: Effect[F]): Option[A] =
    awaitOutputUnsafe(d).map(oa => oa.value)
}

object EndpointResult {

  final case class Matched[F[_], A](
      rem: Input,
      trc: Trace,
      out: F[Output[A]]
  ) extends EndpointResult[F, A]

  abstract class NotMatched[F[_]] extends EndpointResult[F, Nothing]

  object NotMatched extends NotMatched[Id] {
    final case class MethodNotAllowed[F[_]](allowed: List[Method]) extends NotMatched[F]

    def apply[F[_]]: NotMatched[F] = NotMatched.asInstanceOf[NotMatched[F]]
  }

  implicit class EndpointResultOps[F[_], A](val self: EndpointResult[F, A]) extends AnyVal {

    /**
      * Returns the [[Output]] if an [[Endpoint]] is matched.
      */
    final def output: Option[F[Output[A]]] = self match {
      case EndpointResult.Matched(_, _, out) => Some(out)
      case _                                 => None
    }
  }
}
