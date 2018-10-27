package io.finch.circe.test

import io.circe.generic.auto._
import io.finch.test.AbstractJsonSpec
import io.iteratee.Enumerator
import scala.util.Try

class CirceSpec extends AbstractJsonSpec {
  import io.finch.circe._
  checkJson("circe")
  checkStreamJson[Enumerator, Try]("circe")(Enumerator.enumList, _.toVector.get.toList)
}

class CirceAccumulatingSpec extends AbstractJsonSpec {
  import io.finch.circe.accumulating._
  checkJson("circe-accumulating")
  checkStreamJson[Enumerator, Try]("circe-accumulating")(Enumerator.enumList, _.toVector.get.toList)
}

class CirceDropNullKeysSpec extends AbstractJsonSpec {
  import io.finch.circe.dropNullValues._
  checkJson("circe-dropNullKeys")
}

class CircePredictSizeSpec extends AbstractJsonSpec {
  import io.finch.circe.predictSize._
  checkJson("circe-predictSize")
}
