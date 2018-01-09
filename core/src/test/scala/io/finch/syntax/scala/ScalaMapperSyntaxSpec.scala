package io.finch.syntax.scala

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import io.finch.syntax.MapperSyntaxSpec

class ScalaMapperSyntaxSpec extends MapperSyntaxSpec {

  import scalaFutures._

  it should behave like endpointMapper[Future]
}
