package io.finch

import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response

/** [[FinchContext]] provides `Response.Schema.Field`s that Finch uses to export internal
 * information.
 */
object FinchContext {

  /** [[RequestPathField]] exports the [[Endpoint toString]] of the endpoint that is matched. */
  val RequestPathField: Request.Schema.Field[String] = Request.Schema.newField[String]()

  /** [[ResponsePathField]] exports the [[Endpoint toString]] of the endpoint that is matched. */
  val ResponsePathField: Response.Schema.Field[String] = Response.Schema.newField[String]()
}
