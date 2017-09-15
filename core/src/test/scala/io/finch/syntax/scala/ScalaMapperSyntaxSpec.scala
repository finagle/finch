package io.finch.syntax.scala

import scala.concurrent.ExecutionContext.Implicits.global

import io.finch.syntax.MapperSyntaxBehaviour

class ScalaMapperSyntaxSpec extends MapperSyntaxBehaviour {

  it should behave like endpointMapper(scalaToTwitterFuture)

}
