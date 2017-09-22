package io.finch.syntax.scala

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import io.finch.syntax.MapperSyntaxBehaviour

class ScalaMapperSyntaxSpec extends MapperSyntaxBehaviour {

  it should behave like endpointMapper[Future]

}
