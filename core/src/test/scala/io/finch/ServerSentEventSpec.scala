package io.finch

import java.nio.charset.{Charset, StandardCharsets}

import cats.Show
import com.twitter.concurrent.AsyncStream
import com.twitter.io.Buf
import io.finch.internal.HttpContent
import org.scalacheck.Gen.Choose
import org.scalacheck.Prop.propBoolean
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

class ServerSentEventSpec extends AnyFlatSpec with Matchers with Checkers {

  behavior of "ServerSentEvent"

  private[this] def text(s: String, cs: Charset): Buf = Buf.ByteArray.Owned(s.getBytes(cs.name))

  import ServerSentEvent._

  def genCharset: Gen[Charset] = Gen.oneOf(
    StandardCharsets.ISO_8859_1,
    StandardCharsets.US_ASCII,
    StandardCharsets.UTF_8,
    StandardCharsets.UTF_16,
    StandardCharsets.UTF_16BE,
    StandardCharsets.UTF_16LE
  )

  implicit def arbitraryCharset: Arbitrary[Charset] = Arbitrary(genCharset)

  def dataOnlySse: Gen[ServerSentEvent[String]] = for {
    data <- Gen.alphaStr
  } yield ServerSentEvent(data)

  def sseWithId: Gen[ServerSentEvent[String]] = for {
    sse <- dataOnlySse
    id <- Gen.alphaStr
  } yield sse.copy(id = Some(id))

  def sseWithEventType: Gen[ServerSentEvent[String]] = for {
    sse <- dataOnlySse
    eventType <- Gen.alphaStr
  } yield sse.copy(event = Some(eventType))

  def sseWithRetry: Gen[ServerSentEvent[String]] = for {
    sse <- dataOnlySse
    retry <- Choose.chooseLong.choose(-1000, 1000)
  } yield sse.copy(retry = Some(retry))

  def streamDataOnlyGenerator: Gen[AsyncStream[ServerSentEvent[String]]] = for {
    strs <- Gen.nonEmptyListOf(dataOnlySse)
  } yield AsyncStream.fromSeq(strs)

  implicit def arbitrarySse: Arbitrary[AsyncStream[ServerSentEvent[String]]] =
    Arbitrary(streamDataOnlyGenerator)

  val encoder = encodeEventStream[String](Show.fromToString)

  it should "encode the event when only 'data' is present" in {
    implicit def arbitraryEvents: Arbitrary[ServerSentEvent[String]] = Arbitrary(dataOnlySse)

    check { (event: ServerSentEvent[String], cs: Charset) =>
      val encoded = encoder(event, cs)
      val expected = Buf(Vector(text("data:", cs), text(event.data, cs), text("\n", cs)))
      encoded === expected
    }
  }

  it should "encode the event when an 'eventType' is present" in {
    implicit def arbitraryEvents: Arbitrary[ServerSentEvent[String]] = Arbitrary(sseWithEventType)

    check { (event: ServerSentEvent[String], cs: Charset) =>
      (event.event.isDefined && event.id.isEmpty && event.retry.isEmpty) ==> {
        val encoded = encoder(event, cs)
        val actualText = encoded.asString(cs)
        val expectedParts = Buf(
          Vector(
            text("data:", cs),
            text(event.data, cs),
            text("\n", cs),
            text(s"event:${event.event.get}\n", cs)
          )
        )
        actualText === expectedParts.asString(cs)
      }
    }
  }

  it should "encode the event when an 'id' is present" in {
    implicit def arbitraryEvents: Arbitrary[ServerSentEvent[String]] = Arbitrary(sseWithId)

    check { (event: ServerSentEvent[String], cs: Charset) =>
      (event.event.isEmpty && event.id.isDefined && event.retry.isEmpty) ==> {
        val encoded = encoder(event, cs)
        val actualText = encoded.asString(cs)
        val expectedParts = Buf(
          Vector(
            text("data:", cs),
            text(event.data, cs),
            text("\n", cs),
            text(s"id:${event.id.get}\n", cs)
          )
        )
        actualText === expectedParts.asString(cs)
      }
    }
  }

  it should "encode the event when a 'retry' is present" in {
    implicit def arbitraryEvents: Arbitrary[ServerSentEvent[String]] = Arbitrary(sseWithRetry)

    check { (event: ServerSentEvent[String], cs: Charset) =>
      (event.event.isEmpty && event.id.isEmpty && event.retry.isDefined) ==> {
        val encoded = encoder(event, cs)
        val actualText = encoded.asString(cs)
        val expectedParts = Buf(
          Vector(
            text("data:", cs),
            text(event.data, cs),
            text("\n", cs),
            text(s"retry:${event.retry.get}\n", cs)
          )
        )
        actualText === expectedParts.asString(cs)
      }
    }
  }
}
