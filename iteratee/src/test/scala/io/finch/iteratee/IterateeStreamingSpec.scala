package io.finch.iteratee

import cats.effect.IO
import com.twitter.io.Buf
import io.finch._
import io.finch.streaming.StreamDecoder
import io.iteratee.Enumerator
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class IterateeStreamingSpec extends FinchSpec with GeneratorDrivenPropertyChecks {

  private implicit val bufStreamDecoder: StreamDecoder.Aux[Enumerator, IO, Buf, Text.Plain] = {
    StreamDecoder.instance[Enumerator, IO, Buf, Text.Plain]((enum, _) => {
      enum.map(identity)
    })
  }

  behavior of "iteratee"

  checkAll("streamBody", StreamingLaws[Enumerator, IO, Buf, Text.Plain](
    Enumerator.enumList,
    _.toVector.unsafeRunSync().toList
  ).all)

}
