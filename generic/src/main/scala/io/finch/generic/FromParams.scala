package io.finch.generic

import cats.data.NonEmptyList
import cats.effect.Effect
import io.finch._
import io.finch.endpoint._
import scala.reflect.ClassTag
import shapeless._
import shapeless.labelled._
import shapeless.poly._

/**
 * A type class empowering a generic derivation of [[Endpoint]]s from query string params.
 */
trait FromParams[F[_], L <: HList] {
  def endpoint: Endpoint[F, L]
}

object FromParams {

  implicit def hnilFromParams[F[_] : Effect]: FromParams[F, HNil] = new FromParams[F, HNil] {
    def endpoint: Endpoint[F, HNil] = Endpoint.const[F, HNil](HNil)
  }

  implicit def hconsFromParams[F[_] : Effect, HK <: Symbol, HV, T <: HList](implicit
    key: Witness.Aux[HK],
    fpt: FromParams[F, T],
    hс: Case1.Aux[Extractor.type, String, Endpoint[F, HV]]
  ): FromParams[F, FieldType[HK, HV] :: T] = new FromParams[F, FieldType[HK, HV] :: T] {
    def endpoint: Endpoint[F, FieldType[HK, HV] :: T] = {
      hс(key.value.name).map(field[HK](_)) :: fpt.endpoint
    }
  }
}

private[generic] object Extractor extends Poly1 {

  implicit def optionalExtractor[F[_] : Effect, V](implicit
    dh: DecodeEntity[V],
    ct: ClassTag[V]
  ): Case.Aux[String, Endpoint[F, Option[V]]] = at[String] { key =>
    paramOption[F, V](key)
  }

  implicit def seqExtractor[F[_] : Effect, V](implicit
    dh: DecodeEntity[V],
    ct: ClassTag[V]
  ): Case.Aux[String, Endpoint[F, Seq[V]]] = at[String] { key =>
    params[F, V](key)
  }

  implicit def nelExtractor[F[_] : Effect, V](implicit
    dh: DecodeEntity[V],
    ct: ClassTag[V]
  ): Case.Aux[String, Endpoint[F, NonEmptyList[V]]] = at[String] { key =>
    paramsNel[F, V](key)
  }

  implicit def extractor[F[_] : Effect, V](implicit
    dh: DecodeEntity[V],
    ct: ClassTag[V]
  ): Case.Aux[String, Endpoint[F, V]] = at[String] { key =>
    param[F, V](key)
  }
}
