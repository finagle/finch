package io.finch

import java.nio.charset.Charset

import cats.Eq
import cats.effect.Effect
import cats.instances.AllInstances
import cats.laws._
import cats.laws.discipline._
import com.twitter.finagle.http.Request
import com.twitter.io.Reader
import io.finch.streaming.{DecodeStream, StreamFromReader}
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

abstract class StreamingLaws[S[_[_], _], F[_] : Effect, A : Eq, CT <: String] extends Laws with AllInstances {

  implicit def streamReader: StreamFromReader[S, F]
  implicit def streamDecoder: DecodeStream.Aux[S, F, A, CT]

  def toResponse: ToResponse.Aux[S[F, A], CT]
  def fromList:  List[A] => S[F, A]
  def toList: S[F, A] => List[A]

  def roundTrip(a: List[A], cs: Charset): IsEq[List[A]] = {
    val req = Request()
    req.setChunked(true)

    Reader.copy(toResponse(fromList(a), cs).reader, req.writer).before(req.writer.close())

    Endpoint
      .streamBody[F, S, A, CT]
      .apply(Input.fromRequest(req))
      .awaitValueUnsafe()
      .map(toList)
      .get <-> a
  }

  def onlyChunked: EndpointResult[F, S[F, A]] = {
    Endpoint
      .streamBody[F, S, A, CT]
      .apply(Input.fromRequest(Request()))
  }

  def all(implicit
    A: Arbitrary[List[A]],
    CS: Arbitrary[Charset]
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
    streamFromList: List[A] => S[F, A],
    listFromStream: S[F, A] => List[A]
  )(implicit
    tr: ToResponse.Aux[S[F, A], CT],
    reader: StreamFromReader[S, F],
    decoder: DecodeStream.Aux[S, F, A, CT]
  ): StreamingLaws[S, F, A, CT] = new StreamingLaws[S, F, A, CT] {
    implicit val streamReader: StreamFromReader[S, F] = reader
    implicit val streamDecoder: DecodeStream.Aux[S, F, A, CT] = decoder
    val toResponse: ToResponse.Aux[S[F, A], CT] = tr
    val fromList: List[A] => S[F, A] = streamFromList
    val toList: S[F, A] => List[A] = listFromStream
  }

}
