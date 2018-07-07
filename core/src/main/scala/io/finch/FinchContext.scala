package io.finch

import com.twitter.finagle.http.Response

object FinchContext {

  val EndpointTrace: Response.Schema.Field[Trace] = Response.Schema.newField[Trace]()

}
