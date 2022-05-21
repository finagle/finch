package io.finch

import cats.effect.SyncIO
import cats.instances.AllInstances
import cats.laws._
import cats.laws.discipline._
import com.twitter.finagle.http.Request
import com.twitter.io.{Buf, Pipe}
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

import java.nio.charset.Charset

abstract class StreamingLaws[S[_[_], _]] extends Laws with AllInstances with MissingInstances {
  implicit def LR: LiftReader[S, SyncIO]

  def toResponse: ToResponse.Aux[SyncIO, S[SyncIO, Buf], Text.Plain]
  def fromList: List[Buf] => S[SyncIO, Buf]
  def toList: S[SyncIO, Array[Byte]] => List[Buf]

  def roundTrip(a: List[Buf], cs: Charset): IsEq[List[Buf]] = {
    val req = Request()
    req.setChunked(true)
    val rep = toResponse(fromList(a), cs).unsafeRunSync()
    Pipe.copy(rep.reader, req.writer).ensure(req.writer.close())
    toList(Endpoint.binaryBodyStream[SyncIO, S].apply(Input.fromRequest(req)).value.unsafeRunSync()) <-> a
  }

  def onlyChunked: EndpointResult[SyncIO, S[SyncIO, Array[Byte]]] =
    Endpoint.binaryBodyStream[SyncIO, S].apply(Input.fromRequest(Request()))

  def all(implicit
      arb: Arbitrary[Buf],
      CS: Arbitrary[Charset]
  ): RuleSet =
    new DefaultRuleSet(
      name = "all",
      parent = None,
      "roundTrip" -> Prop.forAll((a: List[Buf], cs: Charset) => roundTrip(a, cs)),
      "onlyChunked" -> Prop.=?(EndpointResult.NotMatched[SyncIO], onlyChunked)
    )
}

object StreamingLaws {

  def apply[S[_[_], _]](
      streamFromList: List[Buf] => S[SyncIO, Buf],
      listFromStream: S[SyncIO, Array[Byte]] => List[Buf]
  )(implicit
      lr: LiftReader[S, SyncIO],
      tr: ToResponse.Aux[SyncIO, S[SyncIO, Buf], Text.Plain]
  ): StreamingLaws[S] = new StreamingLaws[S] {
    implicit val LR: LiftReader[S, SyncIO] = lr
    val toResponse: ToResponse.Aux[SyncIO, S[SyncIO, Buf], Text.Plain] = tr
    val fromList: List[Buf] => S[SyncIO, Buf] = streamFromList
    val toList: S[SyncIO, Array[Byte]] => List[Buf] = listFromStream
  }
}
