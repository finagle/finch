package io.finch.div

import cats.instances.int._
import com.twitter.finagle.Http
import com.twitter.util.Await
import io.finch.Text
import io.finch.catsEffect._
import io.finch.catsEffect.syntax._

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

  // We can serve Ints as plain/text responses since there is cats.Show[Int]
  // available via the cats.instances.int._ import.
  def div: Endpoint[Int] = post(path[Int] :: path[Int]) { (a: Int, b: Int) =>
    Ok(a / b)
  } handle {
    case e: ArithmeticException => BadRequest(e)
  }

  Await.ready(Http.server.serve(":8081", div.toServiceAs[Text.Plain]))
}
