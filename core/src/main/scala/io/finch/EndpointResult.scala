package io.finch

import cats.{ApplicativeThrow, Id}
import com.twitter.finagle.http.Method

/** A result returned from an [[Endpoint]]. This models `Option[(Input, Future[Output])]` and represents two cases:
  *
  *   - Endpoint is matched (think of 200).
  *   - Endpoint is not matched (think of 404, 405, etc).
  *
  * In its current state, `EndpointResult.NotMatched` represented with two cases:
  *
  *   - `EndpointResult.NotMatched` (very generic result usually indicating 404)
  *   - `EndpointResult.NotMatched.MethodNotAllowed` (indicates 405)
  */
sealed abstract class EndpointResult[F[_], +A] {

  /** Whether the [[Endpoint]] is matched on a given [[Input]]. */
  final def isMatched: Boolean = this match {
    case EndpointResult.Matched(_, _, _) => true
    case _                               => false
  }

  /** Returns the remainder of the [[Input]] after an [[Endpoint]] is matched. */
  final def remainder: Option[Input] = this match {
    case EndpointResult.Matched(rem, _, _) => Some(rem)
    case _                                 => None
  }

  /** Returns the [[Trace]] if an [[Endpoint]] is matched. */
  final def trace: Option[Trace] = this match {
    case EndpointResult.Matched(_, trc, _) => Some(trc)
    case _                                 => None
  }
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

    /** Returns the [[Output]] if an [[Endpoint]] is matched. */
    def outputAttempt(implicit F: ApplicativeThrow[F]): F[Either[Throwable, Output[A]]] =
      F.attempt(output)

    def outputOption(implicit F: ApplicativeThrow[F]): F[Option[Output[A]]] =
      F.map(outputAttempt)(_.toOption)

    def output(implicit F: ApplicativeThrow[F]): F[Output[A]] = self match {
      case EndpointResult.Matched(_, _, out) => out
      case _                                 => F.raiseError(NotMatchedError)
    }

    def valueAttempt(implicit F: ApplicativeThrow[F]): F[Either[Throwable, A]] =
      F.attempt(value)

    def valueOption(implicit F: ApplicativeThrow[F]): F[Option[A]] =
      F.map(valueAttempt)(_.toOption)

    def value(implicit F: ApplicativeThrow[F]): F[A] =
      F.map(output)(_.value)
  }

  private case object NotMatchedError extends NoSuchElementException("Endpoint didn't match")
}
