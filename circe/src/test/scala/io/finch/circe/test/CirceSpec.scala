package io.finch.circe.test

import io.circe.generic.auto._
import io.finch.test.AbstractJsonSpec

class CirceSpec extends AbstractJsonSpec {
  import io.finch.circe._
  checkJson("circe")
  checkEnumerateJson("circe")
}

class CirceJacksonSpec extends AbstractJsonSpec {
  import io.finch.circe.jacksonSerializer._
  checkJson("circe-jackson")
}

class CirceDropNullKeysSpec extends AbstractJsonSpec {
  import io.finch.circe.dropNullValues._
  checkJson("circe-dropNullKeys")
}
