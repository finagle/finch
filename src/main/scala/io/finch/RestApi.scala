package io.finch

import com.twitter.finagle.http.service.RoutingService
import com.twitter.finagle.{Filter, Service}
import java.net.InetSocketAddress
import com.twitter.finagle.http.{RichHttp, Http}
import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.http.path.Path
import scala.util.Random

class RestApi extends App {

  implicit class FilterAndThenResource(filter: Filter[HttpRequest, HttpResponse, HttpRequest, HttpResponse]) {
      def andThen(resource: Resource) = resource andThen { filter andThen _ }
  }

  def name = "FinchInstance-" + new Random().alphanumeric.take(20)

  def exposeAt(port: Int)(resource: => Resource): Unit = {

    val service = new RoutingService[HttpRequest](
      new PartialFunction[HttpRequest, Service[HttpRequest, HttpResponse]] {
        def apply(req: HttpRequest) = resource.route(req.method -> Path(req.path))
        def isDefinedAt(req: HttpRequest) = resource.route.isDefinedAt(req.method -> Path(req.path))
      })

    ServerBuilder()
    .codec(RichHttp[HttpRequest](Http()))
    .bindTo(new InetSocketAddress(port))
    .name(name)
    .build(service)
  }
}
