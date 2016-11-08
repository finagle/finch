package io.finch.internal

import scala.reflect.ClassTag

import cats.data.NonEmptyList
import io.catbird.util.Rerunnable
import io.finch._
import shapeless._
import shapeless.labelled._
import shapeless.poly._

/**
 * A type class empowering a generic derivation of [[Endpoint]]s from query string params.
 */
trait FromParams[L <: HList] {
  def endpoint: Endpoint[L]
}

object FromParams {

  implicit val hnilFromParams: FromParams[HNil] = new FromParams[HNil] {
    def endpoint: Endpoint[HNil] = Endpoint.embed(items.MultipleItems)(input =>
      Some(input -> Rerunnable(Output.payload(HNil)))
    )
  }

  implicit def hconsFromParams[HK <: Symbol, HV, T <: HList](implicit
    key: Witness.Aux[HK],
    fpt: FromParams[T],
    hс: Case1.Aux[Extractor.type, String, Endpoint[HV]]
  ): FromParams[FieldType[HK, HV] :: T] = new FromParams[FieldType[HK, HV] :: T] {
    def endpoint: Endpoint[FieldType[HK, HV] :: T] = {
      hс(key.value.name).map(field[HK](_)) :: fpt.endpoint
    }
  }
}

private[internal] object Extractor extends Poly1 {

  implicit def optionalExtractor[V](implicit
    dh: DecodeEntity[V],
    ct: ClassTag[V]
  ): Case.Aux[String, Endpoint[Option[V]]] = at[String] { key =>
    paramOption(key).as(dh, ct)
  }

  implicit def seqExtractor[V](implicit
    dh: DecodeEntity[V],
    ct: ClassTag[V]
  ): Case.Aux[String, Endpoint[Seq[V]]] = at[String] { key =>
    params(key).as(dh, ct)
  }

  implicit def nelExtractor[V](implicit
    dh: DecodeEntity[V],
    ct: ClassTag[V]
  ): Case.Aux[String, Endpoint[NonEmptyList[V]]] = at[String] { key =>
    paramsNel(key).as(dh, ct)
  }

  implicit def extractor[V](implicit
    dh: DecodeEntity[V],
    ct: ClassTag[V]
  ): Case.Aux[String, Endpoint[V]] = at[String] { key =>
    param(key).as(dh, ct)
  }
}
