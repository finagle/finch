package io.finch

import com.twitter.util.{Return, Throw}
import eu.timepit.refined._
import eu.timepit.refined.api.{Refined, Validate}

package object refined {

  implicit def decodePathRefined[A, B](implicit ad: DecodePath[A], v: Validate[A, B]): DecodePath[A Refined B] =
    DecodePath.instance(s => ad(s).flatMap(p => refineV[B](p).toOption))

  implicit def decodeEntityRefined[A, B](implicit ad: DecodeEntity[A], v: Validate[A, B]): DecodeEntity[A Refined B] =
    DecodeEntity.instance(s => ad(s).flatMap(e => refineV[B](e) match {
      case Left(error) => Throw(PredicateFailed(error))
      case Right(ref) => Return(ref)
    }))

}
