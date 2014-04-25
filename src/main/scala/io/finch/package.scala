package io

import scala.util.parsing.json.{JSONArray, JSONObject}
import com.twitter.util.Future
import com.twitter.finagle.{Service, Filter}
import com.twitter.finagle.http.{Status, Version, Response}

package object finch {
  type HttpRequest = com.twitter.finagle.http.Request
  type HttpResponse = com.twitter.finagle.http.Response
  type JsonResponse = scala.util.parsing.json.JSONType

  object JsonObject {
    def apply(args: (String, Any)*) = JSONObject(args.toMap)
  }

  object JsonArray {
    def apply(args: JSONObject*) = JSONArray(args.toList)
  }

  implicit class JsonToFuture(json: JsonResponse) {
      def toFuture: Future[JsonResponse] = Future.value(json)
  }

  trait Facet[+RepIn, -RepOut] extends Filter[HttpRequest, RepOut, HttpRequest, RepIn]

  object TurnJsonToHttp extends Facet[JsonResponse, HttpResponse] {
    def apply(req: HttpRequest, service: Service[HttpRequest, JsonResponse]): Future[HttpResponse] =
      service(req) flatMap { rep =>
        val rep = Response(Version.Http11, Status.Ok)
        rep.setContentTypeJson()
        rep.setContentString(rep.toString())

        Future.value(rep)
      }
  }
}
