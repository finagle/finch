package io.finch.request

import com.twitter.finagle.httpx.Request
import com.twitter.util.Future
import io.finch._
import io.finch.request.items._

/**
 * Convenience methods for creating new reader instances.
 */
object RequestReader {

  /**
   * Creates a new [[io.finch.request.RequestReader RequestReader]] that always succeeds, producing the specified value.
   *
   * @param value the value the new reader should produce
   * @return a new reader that always succeeds, producing the specified value
   */
  def value[A](value: A): RequestReader[A] = const[A](value.toFuture)

  /**
   * Creates a new [[io.finch.request.RequestReader RequestReader]] that always fails, producing the specified
   * exception.
   *
   * @param exc the exception the new reader should produce
   * @return a new reader that always fails, producing the specified exception
   */
  def exception[A](exc: Throwable): RequestReader[A] = const[A](exc.toFutureException[A])

  /**
   * Creates a new [[io.finch.request.RequestReader RequestReader]] that always produces the specified value. It will
   * succeed if the given `Future` succeeds and fail if the `Future` fails.
   *
   * @param value the value the new reader should produce
   * @return a new reader that always produces the specified value
   */
  def const[A](value: Future[A]): RequestReader[A] = embed[Request, A](MultipleItems)(_ => value)

  /**
   * Creates a new [[io.finch.request.RequestReader RequestReader]] that reads the result from the request.
   *
   * @param f the function to apply to the request
   * @return a new reader that reads the result from the request
   */
  def apply[R, A](f: R => A): PRequestReader[R, A] = embed[R, A](MultipleItems)(f(_).toFuture)

  private[request] def embed[R, A](i: RequestItem)(f: R => Future[A]): PRequestReader[R, A] =
    new PRequestReader[R, A] {
      val item = i
      def apply(req: R): Future[A] = f(req)
    }

  import scala.reflect.ClassTag
  import shapeless._, labelled.{FieldType, field}

  class GenericDerivation[A] {
    def fromParams[Repr <: HList](implicit
      gen: LabelledGeneric.Aux[A, Repr],
      fp: FromParams[Repr]
    ): RequestReader[A] = fp.reader.map(gen.from)
  }

  trait FromParams[L <: HList] {
    def reader: RequestReader[L]
  }

  object FromParams {
    implicit val hnilFromParams: FromParams[HNil] = new FromParams[HNil] {
      def reader: RequestReader[HNil] = value(HNil)
    }

    implicit def hconsFromParams[HK <: Symbol, HV, T <: HList](implicit
      dh: DecodeRequest[HV],
      ct: ClassTag[HV],
      key: Witness.Aux[HK],
      fpt: FromParams[T]
    ): FromParams[FieldType[HK, HV] :: T] = new FromParams[FieldType[HK, HV] :: T] {
      def reader: RequestReader[FieldType[HK, HV] :: T] =
        param(key.value.name).as(dh, ct).map(field[HK](_)) :: fpt.reader
    }
  }

  def derive[A]: GenericDerivation[A] = new GenericDerivation[A]
}
