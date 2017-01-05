package io.finch.circe

import io.circe.generic.auto._
import io.finch.test.AbstractJsonSpec

class CirceSpec extends AbstractJsonSpec { checkJson("circe") }
