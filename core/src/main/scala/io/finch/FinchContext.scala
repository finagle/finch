package io.finch

import com.twitter.finagle.http.Response

/** [[FinchContext]] provides `Response.Schema.Field`s that Finch uses to export internal
 * information.
 */
object FinchContext {

  /** [[PathField]] exports the [[Endpoint toString]] of the endpoint that is matched.
    */
  val PathField: Response.Schema.Field[String] = Response.Schema.newField[String]()
}
