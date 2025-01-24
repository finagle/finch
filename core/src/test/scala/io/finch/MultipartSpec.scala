package io.finch

import cats.Show
import cats.effect.SyncIO
import com.twitter.finagle.http.exp.Multipart
import com.twitter.finagle.http.{FileElement, RequestBuilder, SimpleElement}
import com.twitter.io.Buf
import io.finch.data.Foo

import java.util.UUID
import scala.reflect.ClassTag

class MultipartSpec extends FinchSpec[SyncIO] {

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

  def laws[A: DecodeEntity: Show: ClassTag](k: String) =
    EntityEndpointLaws(multipartAttributeOption[A](k), Dispatchers.forSyncIO)(v => withAttribute(k -> v))

  checkAll("Attribute[String]", laws[String]("nickname").evaluating)
  checkAll("Attribute[Int]", laws[Int]("level").evaluating)
  checkAll("Attribute[Long]", laws[Long]("gold").evaluating)
  checkAll("Attribute[Boolean]", laws[Boolean]("hard-mode").evaluating)
  checkAll("Attribute[Float]", laws[Float]("multiplier").evaluating)
  checkAll("Attribute[Double]", laws[Double]("score").evaluating)
  checkAll("Attribute[UUID]", laws[UUID]("id").evaluating)
  checkAll("Attribute[Foo]", laws[Foo]("foo").evaluating)

  checkAll(
    "EvaluatingAttribute[String]",
    EvaluatingEndpointLaws[SyncIO, String](implicit de => multipartAttribute("foo")).all
  )

  it should "file upload (single)" in
    check { b: Buf =>
      val i = withFileUpload("foo", b)
      val fu = multipartFileUpload("foo").apply(i).valueOption.unsafeRunSync()
      val fuo = multipartFileUploadOption("foo").apply(i).valueOption.unsafeRunSync().flatten

      fu.map(_.asInstanceOf[Multipart.InMemoryFileUpload].content) === Some(b) &&
      fuo.map(_.asInstanceOf[Multipart.InMemoryFileUpload].content) === Some(b)
    }

  it should "fail when attribute is missing" in {
    an[Error.NotPresent] should be thrownBy
      multipartAttribute("foo").apply(Input.get("/")).value.unsafeRunSync()
  }

  it should "return None for when attribute is missing for optional endpoint" in {
    multipartAttributeOption("foo").apply(Input.get("/")).valueOption.unsafeRunSync().flatten shouldBe None
  }

  it should "fail when attributes are missing" in {
    an[Error.NotPresent] should be thrownBy
      multipartAttributesNel("foo").apply(Input.get("/")).value.unsafeRunSync()
  }

  it should "return empty sequence when attributes are missing for seq endpoint" in
    multipartAttributes("foo").apply(Input.get("/")).valueOption.unsafeRunSync() === Some(Seq())

  it should "fail when attribute is malformed" in {
    an[Error.NotParsed] should be thrownBy
      multipartAttribute[Int]("foo").apply(withAttribute("foo" -> "bar")).value.unsafeRunSync()
  }
}
