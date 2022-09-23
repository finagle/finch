package io.finch

import cats.effect.Sync
import cats.effect.std.Dispatcher
import cats.laws._
import cats.laws.discipline._
import cats.syntax.all._
import com.twitter.finagle.http.Request
import com.twitter.io.{Buf, Pipe}
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

import java.nio.charset.Charset

abstract class StreamingLaws[S[_[_], _], F[_]] extends Laws with TestInstances {
  implicit def LR: LiftReader[S, F]
  implicit def F: Sync[F]

  def dispatcher: Dispatcher[F]
  def toResponse: ToResponse.Aux[F, S[F, Buf], Text.Plain]
  def fromList: List[Buf] => S[F, Buf]
  def toList: S[F, Array[Byte]] => F[List[Buf]]

  def roundTrip(a: List[Buf], cs: Charset): IsEq[List[Buf]] = {
    val req = Request()
    req.setChunked(true)
    val rep = dispatcher.unsafeRunSync(toResponse(fromList(a), cs))
    Pipe.copy(rep.reader, req.writer).ensure(req.writer.close())
    dispatcher.unsafeRunSync(Endpoint.binaryBodyStream[F, S].apply(Input.fromRequest(req)).value.flatMap(toList)) <-> a
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

  def apply[S[_[_], _], F[_]: Sync](
      dispatch: Dispatcher[F],
      streamFromList: List[Buf] => S[F, Buf],
      listFromStream: S[F, Array[Byte]] => F[List[Buf]]
  )(implicit
      lr: LiftReader[S, F],
      tr: ToResponse.Aux[F, S[F, Buf], Text.Plain]
  ): StreamingLaws[S, F] = new StreamingLaws[S, F] {
    val LR = lr
    val F = Sync[F]
    val dispatcher = dispatch
    val toResponse = tr
    val fromList = streamFromList
    val toList = listFromStream
  }
}
