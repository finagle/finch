package io.finch.syntax

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ScalaMapperSyntaxSpec extends MapperSyntaxSpec {

  import scalaFutures._

  it should behave like endpointMapper[Future]
}
