package io.finch.syntax

import com.twitter.util.Future
import io.catbird.util._

class TwitterMapperSyntaxSpec extends MapperSyntaxSpec {

  it should behave like endpointMapper[Future]

}
