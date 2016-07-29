package io.finch.pickling

import com.twitter.finagle.Http
import com.twitter.util.{Await, Eval}
import io.finch._

object Main extends App {

  import scala.pickling.Defaults._
  import scala.pickling.json._
  import io.finch.pickling.Converters.{pickleStringWriter, jsonStringReader}

  case class Input(expression: String)

  case class Output(result: String)

  case class Echo(value: Output)

  val execute: Eval = new Eval()

  val hello: Endpoint[String] = get("hello") {
    Ok("Hello, world!")
  } handle {
    case e: Exception => BadRequest(e)
  }

  val echo: Endpoint[Echo] = post("echo" :: body.as[Input]) { i: Input =>
    Ok(Echo(Output(i.expression)))
  } handle {
    case e: Exception => BadRequest(e)
  }

  val eval: Endpoint[Output] = post("eval" :: body.as[Input]) { i: Input =>
    Ok(Output(execute[Any](i.expression).toString))
  } handle {
    case e: Exception => BadRequest(e)
  }

  private val api = hello :+: echo :+: eval

  Await.ready(Http.server.serve(":8081", api.toService))
}
