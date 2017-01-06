package io.finch.eval

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.twitter.finagle.Http
import com.twitter.util.{Await, Eval}
import io.finch._
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

  // See https://github.com/FasterXML/jackson-module-scala/issues/187
  implicit val objectMapper: ObjectMapper with ScalaObjectMapper = {
    val mapper = new ObjectMapper with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)

    mapper
  }

  case class EvalInput(expression: String)
  case class EvalOutput(result: String)

  val execute: Eval = new Eval()

  def eval: Endpoint[EvalOutput] = post("eval" :: jsonBody[EvalInput]) { i: EvalInput =>
    Ok(EvalOutput(execute[Any](i.expression).toString))
  } handle {
    case e: Exception => BadRequest(e)
  }

  def main(): Unit =
    Await.ready(Http.server.serve(":8081", eval.toServiceAs[Application.Json]))
}
