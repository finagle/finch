package io.finch.internal

import scala.reflect.ClassTag

import io.finch._
import shapeless.{::, HList, HNil, Witness}
import shapeless.labelled._

/**
 * A type class empowering a generic derivation of [[Endpoint]]s from query string params.
 */
trait FromParams[L <: HList] {
  def endpoint: Endpoint[L]
}

object FromParams {

  implicit val hnilFromParams: FromParams[HNil] = new FromParams[HNil] {
    def endpoint: Endpoint[HNil] = Endpoint.Empty
  }

  implicit def hconsFromParams[HK <: Symbol, HV, T <: HList](implicit
    dh: Decode[HV],
    ct: ClassTag[HV],
    key: Witness.Aux[HK],
    fpt: FromParams[T]
  ): FromParams[FieldType[HK, HV] :: T] = new FromParams[FieldType[HK, HV] :: T] {
    def endpoint: Endpoint[FieldType[HK, HV] :: T] =
      param(key.value.name).as(dh, ct).map(field[HK](_)) :: fpt.endpoint
  }
}

