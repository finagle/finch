package io.finch.div

import com.twitter.finagle.Http
import com.twitter.util.Await
import io.finch._
import shapeless._

/**
 * A tiny Finch application that serves a single endpoint `POST /:a/b:` that divides `a` by `b`.
 *
 * Use the following sbt command to run the application.
 *
 * {{{
 *   $ sbt 'examples/runMain io.finch.div.Main'
 * }}}
 *
 * Use the following HTTPie commands to test endpoints.
 *
 * {{{
 *   $ http POST :8081/20/10
 *   $ http POST :8081/10/0
 * }}}
 */
object Main extends App {

  val div: Endpoint[String] = post(int :: int) { (a: Int, b: Int) =>
    Ok((a / b).toString)
  } handle {
    case e: ArithmeticException => BadRequest(e)
  }

  Await.ready(Http.server.serve(":8081", div.toServiceAs[Witness.`"text/plain"`.T]))
}
