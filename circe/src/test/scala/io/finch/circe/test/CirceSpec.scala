package io.finch.circe.test

import cats.effect.IO
import fs2.Stream
import io.finch.test.AbstractJsonSpec
import io.iteratee.Enumerator

class CirceSpec extends AbstractJsonSpec {
  import io.finch.circe._
  checkJson("circe")
  checkStreamJson[Enumerator, IO]("circe-iteratee")(Enumerator.enumList, _.toVector.unsafeRunSync().toList)
  checkStreamJson[Stream, IO]("circe-fs2")(
    list => Stream.fromIterator[IO](list.toIterator),
    _.compile.toList.unsafeRunSync()
  )
}

class CirceAccumulatingSpec extends AbstractJsonSpec {
  import io.finch.circe.accumulating._
  checkJson("circe-accumulating")
  checkStreamJson[Enumerator, IO]("circe-accumulating")(Enumerator.enumList, _.toVector.unsafeRunSync().toList)
}

class CirceDropNullKeysSpec extends AbstractJsonSpec {
  import io.finch.circe.dropNullValues._
  checkJson("circe-dropNullKeys")
}

class CircePredictSizeSpec extends AbstractJsonSpec {
  import io.finch.circe.predictSize._
  checkJson("circe-predictSize")
}
