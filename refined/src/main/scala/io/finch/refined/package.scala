package io.finch

import com.twitter.util.{Return, Throw}
import eu.timepit.refined.api.{RefType, Validate}

package object refined {

  implicit def decodePathRefined[F[_, _], A, B](implicit
    ad: DecodePath[A],
    v: Validate[A, B],
    rt: RefType[F]
  ): DecodePath[F[A, B]] = DecodePath.instance(s => ad(s).flatMap(p => rt.refine[B](p).right.toOption))

  implicit def decodeEntityRefined[F[_, _], A, B](implicit
    ad: DecodeEntity[A],
    v: Validate[A, B],
    rt: RefType[F]
  ): DecodeEntity[F[A, B]] =
    DecodeEntity.instance(s => ad(s).flatMap(e => rt.refine[B](e) match {
      case Left(error) => Throw(PredicateFailed(error))
      case Right(ref) => Return(ref)
    }))

}
