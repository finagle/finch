package io.finch

import java.util.UUID
import scala.reflect.ClassTag
import com.twitter.util.{Return, Throw, Try}
import shapeless.{::, Generic, HNil}

/**
 * An abstraction that is responsible for decoding the request of type `A`.
 */
trait DecodeRequest[A] {
  def apply(s: String): Try[A]
}

trait LowPriorityDecodeRequestInstances {

  /**
   * Creates an instance for a given type.
   */
  def instance[A](f: String => Try[A]): DecodeRequest[A] = new DecodeRequest[A] {
    def apply(s: String): Try[A] = f(s)
  }

  /**
   * Creates a [[DecodeRequest]] from [[shapeless.Generic]].
   *
   * Note: This is mostly a workaround for `RequestReader[String].as[CaseClassOfASingleString]`,
   *       because by some reason, compiler doesn't pick `ValueReaderOps` for `RequestReader[String]`.
   */
  implicit def decodeRequestFromGeneric[A](implicit
   gen: Generic.Aux[A, String :: HNil]
   ): DecodeRequest[A] = instance(s => Return(gen.from(s :: HNil)))
}

object DecodeRequest extends LowPriorityDecodeRequestInstances {

  /**
   * Returns an instance for a given type.
   */
  def apply[A](implicit dr: DecodeRequest[A]): DecodeRequest[A] = dr

  private val EmptyString = "None"

  private def seq(s: String): Seq[String] = s.split(",").toSeq.filterNot(x=> x == EmptyString) match {
    case Nil => throw new Throwable("Empty List")
    case x  => x
  }

  private def seqOpt(s:String): Option[Seq[String]] = s.split(",").toSeq.filterNot(x =>
    x == EmptyString) match {
    case Nil => None
    case x  => Some(x)
  }

  private def castSeqOpt[A](s: String,f: String => A): Try[Option[Seq[A]]] =
    Try(seqOpt(s).map(x=> x.flatMap(xx=> cast(xx,f))))

  private def castSeq[A](s: String,f: String => A): Try[Seq[A]] =
    Try(seq(s).flatMap(x=> cast(x,f)))


  private def cast[A](st: String,s: String => A): Option[A] =
    try Some(s(st)) catch { case t: Throwable => None }

  private def castTry[A](st: String,s: String => A) = Try(cast(st,s))

   private def castWithException[A](castTo: String,f: String => A): DecodeRequest[A] =
     instance(s => if (s == EmptyString)
       throw new Throwable(s"Required element of type: $castTo is not present in request params") else Try(f(s)))
  /**
   * A [[DecodeRequest]] instance for `String`.
   */
  implicit val decodeString: DecodeRequest[String] =
    instance(s => if (s == EmptyString)
      throw new Throwable(s"Required element of type: String not present in request params") else Return(s))

  implicit val decodeStringOpt: DecodeRequest[Option[String]] =
    instance(s =>Try(if (s == EmptyString) None else Some(s)))

  implicit val decodeStringSeq: DecodeRequest[Seq[String]] = instance(s =>Try(seq(s)))

  implicit val decodeStringOptSeq: DecodeRequest[Option[Seq[String]]] = instance(s => Try(seqOpt(s)))

  /**
   * A [[DecodeRequest]] instance for `Int`.
   */
  implicit val decodeInt: DecodeRequest[Int] = castWithException("Int",x=> x.toInt)

  implicit val decodeOptionInt: DecodeRequest[Option[Int]] = instance(s => castTry(s, a => a.toInt))

  implicit val decodeIntSeq: DecodeRequest[Seq[Int]] = instance(s => castSeq(s,x=> s.toInt))

  implicit val decodeIntSeqOption: DecodeRequest[Option[Seq[Int]]] = instance(s => castSeqOpt(s,x=> x.toInt))

  /**
   * A [[DecodeRequest]] instance for `Long`.
   */
  implicit val decodeLong: DecodeRequest[Long] = castWithException("Long",x=> x.toLong)

  implicit val decodeLongOption: DecodeRequest[Option[Long]] = instance(s => castTry(s, l => l.toLong))

  implicit val decodeLongSeq: DecodeRequest[Seq[Long]] = instance(s => castSeq(s, l => l.toLong))

  implicit val decodeLongSeqOption: DecodeRequest[Option[Seq[Long]]]= instance(s =>  castSeqOpt(s, l => l.toLong))
  /**
   * A [[DecodeRequest]] instance for `Float`.
   */

  implicit val decodeFloat: DecodeRequest[Float] = castWithException("Float",x=> x.toFloat)

  implicit val decodeFloatOption: DecodeRequest[Option[Float]] = instance(s => castTry(s, x=> x.toFloat))

  implicit val decodeFloatSeq: DecodeRequest[Seq[Float]] = instance(s => castSeq(s, x=> x.toFloat))

  implicit val decodeFloatSeqOption: DecodeRequest[Option[Seq[Float]]] = instance(s => castSeqOpt(s, x=> x.toFloat))

  /**
   * A [[DecodeRequest]] instance for `Double`.
   */
  implicit val decodeDouble: DecodeRequest[Double] = castWithException("Double",x=> x.toDouble)

  implicit val decodeDoubleOption: DecodeRequest[Option[Double]] = instance(s => castTry(s,d => d.toDouble))

  implicit val decodeDoubleSeq: DecodeRequest[Seq[Double]] = instance(s => castSeq(s,d => d.toDouble))

  implicit val decodeDoubleSeqOption: DecodeRequest[Option[Seq[Double]]]= instance(s => castSeqOpt(s,d => d.toDouble))

  /**
   * A [[DecodeRequest]] instance for `Boolean`.
   */
  implicit val decodeBoolean: DecodeRequest[Boolean] = castWithException("Boolean",x=> x.toBoolean)

  implicit val decodeBooleanOption: DecodeRequest[Option[Double]] = instance(s => castTry(s,b => b.toDouble))

  implicit val decodeBooleanSeq: DecodeRequest[Seq[Double]] = instance(s => castSeq(s,b => b.toDouble))

  implicit val decodeBooleanSeqOption: DecodeRequest[Option[Seq[Double]]] = instance(s => castSeqOpt(s,b => b.toDouble))


  /**
   * A [[DecodeRequest]] instance for `UUID`.
   */
  implicit val decodeUUID: DecodeRequest[UUID] = instance(s =>
    if (s.length != 36) Throw(new IllegalArgumentException(s"Too long for UUID: ${s.length}"))
    else Try(UUID.fromString(s))
  )

  /**
   * Creates a [[DecodeRequest]] instance from [[DecodeAnyRequest]].
   */
  implicit def decodeRequestFromAnyDecode[A](implicit
                                             d: DecodeAnyRequest,
                                             tag: ClassTag[A]
                                              ): DecodeRequest[A] = instance(s => d(s)(tag))
}

/**
 * An abstraction that is responsible for decoding the request of general type.
 */
trait DecodeAnyRequest {
  def apply[A: ClassTag](req: String): Try[A]
}
