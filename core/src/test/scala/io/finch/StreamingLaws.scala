package io.finch

import cats.effect.Sync
import cats.effect.std.Dispatcher
import cats.instances.AllInstances
import cats.laws._
import cats.laws.discipline._
import com.twitter.finagle.http.Request
import com.twitter.io.{Buf, Pipe}
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

import java.nio.charset.Charset

abstract class StreamingLaws[S[_[_], _], F[_]: Sync](implicit dispatcher: Dispatcher[F]) extends Laws with AllInstances with MissingInstances {

  implicit def LR: LiftReader[S, F]

  def toResponse: ToResponse.Aux[F, S[F, Buf], Text.Plain]
  def fromList: List[Buf] => S[F, Buf]
  def toList: S[F, Array[Byte]] => List[Buf]

  def roundTrip(a: List[Buf], cs: Charset): IsEq[List[Buf]] = {
    val req = Request()
    req.setChunked(true)

    val rep = dispatcher.unsafeRunSync(toResponse(fromList(a), cs))

    Pipe.copy(rep.reader, req.writer).ensure(req.writer.close())

    Endpoint.binaryBodyStream[F, S].apply(Input.fromRequest(req)).awaitValueUnsafe().map(toList).get <-> a
  }

  def onlyChunked: EndpointResult[F, S[F, Array[Byte]]] =
    Endpoint.binaryBodyStream[F, S].apply(Input.fromRequest(Request()))

  def all(implicit
      arb: Arbitrary[Buf],
      CS: Arbitrary[Charset]
  ): RuleSet =
    new DefaultRuleSet(
      name = "all",
      parent = None,
      "roundTrip" -> Prop.forAll((a: List[Buf], cs: Charset) => roundTrip(a, cs)),
      "onlyChunked" -> Prop.=?(EndpointResult.NotMatched[F], onlyChunked)
    )
}

object StreamingLaws {

  def apply[S[_[_], _], F[_]](
      streamFromList: List[Buf] => S[F, Buf],
      listFromStream: S[F, Array[Byte]] => List[Buf]
  )(implicit
      f: Sync[F],
      dispatcher: Dispatcher[F],
      lr: LiftReader[S, F],
      tr: ToResponse.Aux[F, S[F, Buf], Text.Plain]
  ): StreamingLaws[S, F] = new StreamingLaws[S, F] {
    implicit val LR: LiftReader[S, F] = lr

    val toResponse: ToResponse.Aux[F, S[F, Buf], Text.Plain] = tr
    val fromList: List[Buf] => S[F, Buf] = streamFromList
    val toList: S[F, Array[Byte]] => List[Buf] = listFromStream

  }
}
