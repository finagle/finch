package io.finch

import cats.Show
import cats.effect.SyncIO
import com.twitter.finagle.http.exp.Multipart
import com.twitter.finagle.http.{FileElement, RequestBuilder, SimpleElement}
import com.twitter.io.Buf

class MultipartSpec extends FinchSpec {

  behavior of "multipart*"

  def withFileUpload(name: String, value: Buf): Input =
    Input.fromRequest(
      RequestBuilder().url("http://example.com").add(FileElement(name, value, Some("image/gif"), Some("dealwithit.gif"))).buildFormPost(multipart = true)
    )

  def withAttribute[A: Show](first: (String, A), rest: (String, A)*): Input = {
    val req = RequestBuilder().url("http://example.com").add(SimpleElement(first._1, Show[A].show(first._2)))

    Input.fromRequest(
      rest.foldLeft(req)((builder, attr) => builder.add(SimpleElement(attr._1, Show[A].show(attr._2)))).buildFormPost(multipart = true)
    )
  }

  checkAll("Attribute[String]", EntityEndpointLaws(multipartAttributeOption("x"))(a => withAttribute("x" -> a)).evaluating)
  checkAll("Attribute[Int]", EntityEndpointLaws(multipartAttributeOption("x"))(a => withAttribute("x" -> a)).evaluating)
  checkAll("Attribute[Long]", EntityEndpointLaws(multipartAttributeOption("x"))(a => withAttribute("x" -> a)).evaluating)
  checkAll("Attribute[Boolean]", EntityEndpointLaws(multipartAttributeOption("x"))(a => withAttribute("x" -> a)).evaluating)
  checkAll("Attribute[Float]", EntityEndpointLaws(multipartAttributeOption("x"))(a => withAttribute("x" -> a)).evaluating)
  checkAll("Attribute[Double]", EntityEndpointLaws(multipartAttributeOption("x"))(a => withAttribute("x" -> a)).evaluating)
  checkAll("Attribute[UUID]", EntityEndpointLaws(multipartAttributeOption("x"))(a => withAttribute("x" -> a)).evaluating)
  checkAll("Attribute[Foo]", EntityEndpointLaws(multipartAttributeOption("x"))(a => withAttribute("x" -> a)).evaluating)

  checkAll(
    "EvaluatingAttribute[String]",
    EvaluatingEndpointLaws[SyncIO, String](implicit de => multipartAttribute("foo")).all
  )

  it should "file upload (single)" in {
    check { b: Buf =>
      val i = withFileUpload("foo", b)
      val fu = multipartFileUpload("foo").apply(i).valueOption.unsafeRunSync()
      val fuo = multipartFileUploadOption("foo").apply(i).valueOption.unsafeRunSync().flatten

      fu.map(_.asInstanceOf[Multipart.InMemoryFileUpload].content) === Some(b) &&
      fuo.map(_.asInstanceOf[Multipart.InMemoryFileUpload].content) === Some(b)
    }
  }

  it should "fail when attribute is missing" in {
    an[Error.NotPresent] should be thrownBy {
      multipartAttribute("foo").apply(Input.get("/")).valueOption.unsafeRunSync()
    }
  }

  it should "return None for when attribute is missing for optional endpoint" in {
    multipartAttributeOption("foo").apply(Input.get("/")).valueOption.unsafeRunSync().flatten shouldBe None
  }

  it should "fail when attributes are missing" in {
    an[Error.NotPresent] should be thrownBy {
      multipartAttributesNel("foo").apply(Input.get("/")).valueOption.unsafeRunSync()
    }
  }

  it should "return empty sequence when attributes are missing for seq endpoint" in {
    multipartAttributes("foo").apply(Input.get("/")).valueOption.unsafeRunSync() === Some(Seq())
  }

  it should "fail when attribute is malformed" in {
    an[Error.NotParsed] should be thrownBy {
      multipartAttribute[Int]("foo").apply(withAttribute("foo" -> "bar")).valueOption.unsafeRunSync()
    }
  }
}
