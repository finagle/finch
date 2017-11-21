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

  def withAttribute(name: String, value: String): Input =
    Input.fromRequest(RequestBuilder()
      .url("http://example.com")
      .add(SimpleElement(name, value))
      .buildFormPost(multipart = true)
    )

  it should "file upload" in {
    check { b: Buf =>
      val i = withFileUpload("foo", b)
      val fu = multipartFileUpload("foo")(i).awaitValueUnsafe()
      val fuo = multipartFileUploadOption("foo")(i).awaitValueUnsafe().flatten

      fu.map(_.asInstanceOf[Multipart.InMemoryFileUpload].content) === Some(b) &&
      fuo.map(_.asInstanceOf[Multipart.InMemoryFileUpload].content) === Some(b)
    }
  }

  it should "attribute" in {
    check { s: String =>
      val i = withAttribute("foo", s)
      multipartAttribute("foo")(i).awaitValueUnsafe() === Some(s) &&
      multipartAttributeOption("foo")(i).awaitValueUnsafe().flatten === Some(s)
    }
  }
}
