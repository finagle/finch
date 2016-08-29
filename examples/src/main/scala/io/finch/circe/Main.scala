package io.finch.circe

import java.nio.charset.StandardCharsets

import com.twitter.finagle.Http
import com.twitter.util.Await
import io.circe.generic.auto._
import io.finch._

case class Echo(param: String)
case class Req(word: String, count: Int)

/**
 * A set of simple JSON endpoints using circe.
 *
 * Use the following sbt command to run the application:
 *
 * {{{
 *   $ sbt 'examples/runMain io.finch.circe.Main'
 * }}}
 *
 * The endpoints can be tested using cURL:
 *
 * {{{
 * ~> curl -i http://localhost:8081/simple/foo
 * HTTP/1.1 200 OK
 * Content-Type: application/json;charset=utf-8
 * Content-Length: 15
 *
 * {"param":"foo"}
 *
 * ~> curl -i http://localhost:8081/echo/bar/1
 * HTTP/1.1 200 OK
 * Content-Type: application/json;charset=utf-8
 * Content-Length: 15
 *
 * {"param":"bar"}
 *
 * ~> curl -i http://localhost:8081/echo/bar/3
 * HTTP/1.1 200 OK
 * Content-Type: application/json;charset=utf-8
 * Content-Length: 21
 *
 * {"param":"barbarbar"}
 *
 * ~> curl -H "Content-Type: application/json" -X \
 * POST -d '{"word": "hello", "count": "3"}' \
 * -i http://localhost:8081/echo
 * HTTP/1.1 200 OK
 * Content-Type: application/json
 * Content-Length: 27
 *
 * {"param":"hellohellohello"}
 * }}}
 */
object Main extends App {
  val simple = get("simple" :: string) { (s: String) => Ok(Echo(s))}
  val echoCount = get("echo" :: string :: int) {
    (s: String, i: Int) => Ok(Echo(s * i))
  } withCharset StandardCharsets.UTF_8
  val postCount = post("echo" :: body.as[Req]) { (r: Req) => Ok(Echo(r.word * r.count)) }

  Await.ready(Http.server.serve(":8081", (simple :+: echoCount :+: postCount).toServiceAs[Application.Json]))
}
