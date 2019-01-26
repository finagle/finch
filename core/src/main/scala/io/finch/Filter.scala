package io.finch

import com.twitter.finagle.http.{Request, Response}

trait Filter[F[_]] extends ((Request, EndpointCompiled[F]) => F[(Trace, Response)]) {
  self =>

  def andThen(s: EndpointCompiled[F]): EndpointCompiled[F] = new Endpoint.Compiled[F] {
    def apply(req: Request): F[(Trace, Response)] = {
      self(req, s)
    }
  }

  def andThen(other: Filter[F]): Filter[F] = {
    new Filter[F] {
      def apply(req: Request, s: EndpointCompiled[F]): F[(Trace, Response)] = self.andThen(other.andThen(s))(req)
    }
  }

}
