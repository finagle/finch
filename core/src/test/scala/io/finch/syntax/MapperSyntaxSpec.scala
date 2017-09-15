package io.finch.syntax

class MapperSyntaxSpec extends MapperSyntaxBehaviour {

  it should behave like endpointMapper(ToTwitterFuture.identity)

}
