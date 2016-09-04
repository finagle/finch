package io.finch.eval

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.finagle.Http
import com.twitter.util.{Await, Eval}
import io.finch._
import io.finch.internal.BufText
import io.finch.jackson._

/**
 * A simple Finch application evaluating the given Scala expression using
 * [[https://github.com/twitter/util/blob/master/util-eval/src/main/scala/com/twitter/util/Eval.scala util-eval]].
 *
 * Use the following sbt command to run the application.
 *
 * {{{
 *   $ sbt 'examples/runMain io.finch.eval.Main'
 * }}}
 *
 * Use the following HTTPie commands to test endpoints.
 *
 * {{{
 *   $ http POST :8081/eval expression=10+10
 *   $ http POST :8081/eval expression=\"abc\".toList.headOption
 * }}}
 */
object Main {

  implicit val objectMapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  implicit val ee: Encode.Json[Exception] = Encode.json((e, cs) =>
    BufText(objectMapper.writeValueAsString(Map("error" -> e.getMessage)), cs)
  )

  case class Input(expression: String)
  case class Output(result: String)

  val execute: Eval = new Eval()

  def eval: Endpoint[Output] = post("eval" :: body.as[Input]) { i: Input =>
    Ok(Output(execute[Any](i.expression).toString))
  } handle {
    case e: Exception => BadRequest(e)
  }

  def main(): Unit =
    Await.ready(Http.server.serve(":8081", eval.toService))
}
