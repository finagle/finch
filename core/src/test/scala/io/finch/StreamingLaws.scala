package io.finch

import java.nio.charset.Charset

import cats.Eq
import cats.effect.Effect
import cats.instances.AllInstances
import cats.laws._
import cats.laws.discipline._
import com.twitter.finagle.http.Request
import com.twitter.io.{Buf, Writer}
import com.twitter.util.Future
import io.finch
import io.finch.streaming.{StreamDecoder, StreamFromReader}
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

abstract class StreamingLaws[S[_[_], _], F[_] : Effect, A : Eq, CT <: String] extends Laws with AllInstances {

  def encode: Encode.Aux[A, CT]
  def fromList:  List[A] => S[F, A]
  def toList: S[F, A] => List[A]

  def roundTrip(a: List[A], cs: Charset)(implicit
    streamReader: StreamFromReader[S, F],
    streamDecoder: StreamDecoder.Aux[S, F, A, CT]
  ): IsEq[List[A]] = {
    val req = Request()
    req.setChunked(true)
    def write(list: List[A], writer: Writer[Buf]): Future[Unit] = {
      list match {
        case Nil => writer.close()
        case head :: tail => writer.write(encode(head, cs).concat(Buf.Utf8("\n"))).before(write(tail, writer))
      }
    }

    write(a, req.writer)

    Endpoint
      .streamBody[F, S, A, CT]
      .apply(Input.fromRequest(req))
      .awaitValueUnsafe()
      .map(toList)
      .get <-> a
  }

  def onlyChunked(implicit
    streamReader: StreamFromReader[S, F],
    streamDecoder: StreamDecoder.Aux[S, F, A, CT]
  ): EndpointResult[F, S[F, A]] = {
    Endpoint
      .streamBody[F, S, A, CT]
      .apply(Input.fromRequest(Request()))
  }

  def all(implicit
    A: Arbitrary[List[A]],
    CS: Arbitrary[Charset],
    streamReader: StreamFromReader[S, F],
    streamDecoder: StreamDecoder.Aux[S, F, A, CT]
  ): RuleSet =
    new DefaultRuleSet(
      name = "all",
      parent = None,
      "roundTrip" -> Prop.forAll { (a: List[A], cs: Charset) => roundTrip(a, cs) },
      "onlyChunked" -> Prop.=?(EndpointResult.NotMatched[F], onlyChunked)
    )
}

object StreamingLaws {

  def apply[S[_[_], _], F[_] : Effect, A : Eq, CT <: String](
    _fromList: List[A] => S[F, A],
    _toList: S[F, A] => List[A]
  )(implicit enc: Encode.Aux[A, CT]): StreamingLaws[S, F, A, CT] = new StreamingLaws[S, F, A, CT] {
    val encode: finch.Encode.Aux[A, CT] = enc
    val fromList: List[A] => S[F, A] = _fromList
    val toList: S[F, A] => List[A] = _toList
  }

}
