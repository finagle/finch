package io.finch

import com.twitter.finagle.http.{FileElement, RequestBuilder, SimpleElement}
import com.twitter.finagle.http.exp.Multipart
import com.twitter.io.Buf

class MultipartSpec extends FinchSpec {

  behavior of "multipart*"

  def withFileUpload(name: String, value: Buf): Input =
    Input.fromRequest(RequestBuilder()
      .url("http://example.com")
      .add(FileElement(name, value, Some("image/gif"), Some("dealwithit.gif")))
      .buildFormPost(multipart = true)
    )

  def withAttribute(first: (String, String), rest: (String, String)*): Input = {
    val req = RequestBuilder()
      .url("http://example.com")
      .add(SimpleElement(first._1, first._2))

    Input.fromRequest(
      rest.foldLeft(req)((builder, attr) =>
        builder.add(SimpleElement(attr._1, attr._2))
      ).buildFormPost(multipart = true)
    )
  }

  it should "file upload (single)" in {
    check { b: Buf =>
      val i = withFileUpload("foo", b)
      val fu = multipartFileUpload("foo")(i).awaitValueUnsafe()
      val fuo = multipartFileUploadOption("foo")(i).awaitValueUnsafe().flatten

      fu.map(_.asInstanceOf[Multipart.InMemoryFileUpload].content) === Some(b) &&
      fuo.map(_.asInstanceOf[Multipart.InMemoryFileUpload].content) === Some(b)
    }
  }

  it should "attribute (single)" in {
    check { s: String =>
      val i = withAttribute("foo" -> s)

      multipartAttribute("foo").apply(i).awaitValueUnsafe() === Some(s) &&
      multipartAttributeOption("foo").apply(i).awaitValueUnsafe().flatten === Some(s)
    }
  }

  it should "attribute (multiple)" in {
    check { (a: String, b: String) =>
      val i = withAttribute("foo" -> a, "foo" -> b)

      multipartAttributes("foo").apply(i).awaitValueUnsafe() === Some(Seq(a, b)) &&
      multipartAttributesNel("foo").apply(i).awaitValueUnsafe().map(_.toList) === Some(List(a, b))
    }
  }
}
