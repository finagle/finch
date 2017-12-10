package io.finch

import io.finch.syntax.EndpointMapper

class EndpointMetadataSpec extends FinchSpec {

  behavior of "EndpointMetadata"

  private def interpreter(ms: EndpointMetadata): Endpoint[_] = ms match {
    case EndpointMetadata.Method(m, meta) => new EndpointMapper(m, interpreter(meta))
    case EndpointMetadata.Path(s) => s match {
      case Segment.Part(part) => path(part)
      case Segment.Wildcard => *
      case Segment.Empty => /
    }
    case EndpointMetadata.Multipart(name) => multipartAttribute(name)
    case EndpointMetadata.Cookie(name) => cookie(name)
    case EndpointMetadata.Parameter(name) => param(name)
    case EndpointMetadata.Parameters(name) => params(name)
    case EndpointMetadata.Header(name) => header(name)
    case EndpointMetadata.Body => stringBody
    case EndpointMetadata.Empty => Endpoint.empty[String]
    case EndpointMetadata.Const => Endpoint.const("foo")
    case EndpointMetadata.Coproduct(a, b) => interpreter(b) :+: interpreter(a)
    case EndpointMetadata.Product(a, b) => interpreter(a) :: interpreter(b)
  }

  it should "do a round-trip" in {
    check { l: EndpointMetadata =>
      interpreter(l).meta === l
    }
  }
}
