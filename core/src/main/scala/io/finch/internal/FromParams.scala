package io.finch.internal

import scala.reflect.ClassTag

import io.finch._
import shapeless.{::, HList, HNil, Witness}
import shapeless.labelled._

/**
 * A type class empowering a generic derivation of [[RequestReader]]s from query string params.
 */
trait FromParams[L <: HList] {
  def reader: RequestReader[L]
}

object FromParams {

  implicit val hnilFromParams: FromParams[HNil] = new FromParams[HNil] {
    def reader: RequestReader[HNil] = RequestReader.value(HNil)
  }

  implicit def hconsFromParams[HK <: Symbol, HV, T <: HList](implicit
    dh: DecodeRequest[HV],
    ct: ClassTag[HV],
    key: Witness.Aux[HK],
    fpt: FromParams[T]
  ): FromParams[FieldType[HK, HV] :: T] = new FromParams[FieldType[HK, HV] :: T] {
    def reader: RequestReader[FieldType[HK, HV] :: T] =
      paramIncompletes(key.value.name).as(dh,ct).map(field[HK](_)) :: fpt.reader
  }
}

