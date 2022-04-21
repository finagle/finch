package io.finch.div

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import io.finch._

/** A tiny Finch application that serves a single endpoint `POST /:a/b:` that divides `a` by `b`.
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
object Main extends IOApp with Endpoint.Module[IO] {

  // We can serve Ints as plain/text responses since there is cats.Show[Int]
  // available via the cats.instances.int._ import.
  def div: Endpoint[IO, Int] = post(path[Int] :: path[Int]) { (a: Int, b: Int) =>
    Ok(a / b)
  } handle { case e: ArithmeticException =>
    BadRequest(e)
  }


  override def run(args: List[String]): IO[ExitCode] = {
    Bootstrap[IO]
      .serve[Text.Plain](div)
      .listen(":8081")
      .useForever
  }
}
