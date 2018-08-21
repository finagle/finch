package io.finch.syntax

import io.finch.rerunnable.E

class RerunnableSyntaxSpec extends MapperSyntaxSpec(io.finch.rerunnable, io.finch.rerunnable.syntax) {

  it should behave like endpointMapper

}
