package io.finch

import org.jboss.netty.handler.codec.http.HttpMethod
import com.twitter.finagle.http.path.Path
import com.twitter.finagle.Service

trait Resource { self =>

  /**
   * Returns a route itself.
   *
   * @return
   */
  def route: PartialFunction[(HttpMethod, Path), Service[HttpRequest, HttpResponse]]

  /**
   *
   * @param that
   * @return
   */
  def orElse(that: Resource) = new Resource {
    def route = self.route orElse that.route
  }

  /**
   *
   * @param f
   * @return
   */
  def andThen(f: Service[HttpRequest, HttpResponse] => Service[HttpRequest, HttpResponse]) =
    new Resource {
      def route = self.route andThen f
    }

  implicit class AfterThatService[+RepIn](service: Service[HttpRequest, RepIn]) {
    def afterThat[A](thatFacet: Facet[RepIn, A]) =
      thatFacet andThen service
  }

  implicit class AfterThatFacet[+RepIn, -RepOut](facet: Facet[RepIn, RepOut]) {
    def afterThat[A](thatFacet: Facet[RepOut, A]) =
      thatFacet andThen facet
  }
}
