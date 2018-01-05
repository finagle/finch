package io.finch.syntax

import io.finch.syntax.scalafutures._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ScalaMapperSyntaxSpec extends MapperSyntaxSpec {

  it should behave like endpointMapper[Future]

}
