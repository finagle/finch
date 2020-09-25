package io.finch

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
    DecodeEntity.instance { s =>
      ad(s) match {
        case Right(r) =>
          rt.refine[B](r) match {
            case Left(error) => Left(PredicateFailed(error))
            case Right(ref)  => Right(ref)
          }
        case Left(e) => Left(e)
      }
    }

}
