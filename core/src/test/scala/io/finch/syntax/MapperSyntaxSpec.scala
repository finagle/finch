package io.finch.syntax

import com.twitter.util.Future
import io.catbird.util._

class MapperSyntaxSpec extends MapperSyntaxBehaviour {

  it should behave like endpointMapper[Future]

}
