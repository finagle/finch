package io.finch.request

import com.twitter.finagle.httpx.{Request, RequestBuilder}
import com.twitter.util.{Await, Future}
import org.scalatest.{FlatSpec, Matchers}

/**
  * Specification for multipart/form-data request.
  *
  * The request is always a serialized request of a multipart upload
  * from Chrome.
  
  * The resource of the uploads.bytes was taken from:
  * https://github.com/twitter/finatra/blob/5d1d1cbb7640d8c4b1d11a85b53570d11a323e55/src/main/resources/upload.bytes
  * The file was created by using the following form:
  * <form enctype="multipart/form-data" action="/groups_file?debug=true" method="POST">
  *   <label for="groups">Filename:</label>
  *   <input type="file" name="groups" id="groups"><br>
  *   <input type="hidden" name="type" value="text"/>
  *   <input type="submit" name="submit" value="Submit">
  * </form>
  */
class MultipartParamSpec extends FlatSpec with Matchers {

  "A RequiredMultipartFile" should "have a filename" in {
    val request = requestFromBinaryFile("/upload.bytes")
    val futureResult: Future[FileUpload] = fileUpload("groups")(request)
    Await.result(futureResult).getFilename shouldBe "dealwithit.gif"
  }

  it should "have a content type" in {
    val request = requestFromBinaryFile("/upload.bytes")
    val futureResult: Future[FileUpload] = fileUpload("groups")(request)
    Await.result(futureResult).getContentType shouldBe "image/gif"
  }

  it should "have a size greater zero" in {
    val request = requestFromBinaryFile("/upload.bytes")
    val futureResult: Future[FileUpload] = fileUpload("groups")(request)
    Await.result(futureResult).get.size should be > 0
  }

  "An OptionalMultipartFile" should "have a filename if it exists" in {
    val request = requestFromBinaryFile("/upload.bytes")
    val futureResult: Future[Option[FileUpload]] = fileUploadOption("groups")(request)
    Await.result(futureResult).get.getFilename shouldBe "dealwithit.gif"
  }

  it should "be empty when the upload name exists but is not an upload" in {
    val request = RequestBuilder().url("http://localhost/").addFormElement("groups" -> "foo").buildFormPost()
    val futureResult: Future[Option[FileUpload]] = fileUploadOption("groups")(request)
    Await.result(futureResult) shouldBe None
  }

  "A RequiredMultipartParam" should "be properly parsed if it exists" in {
    val request = requestFromBinaryFile("/upload.bytes")
    val futureResult: Future[String] = param("type")(request)
    Await.result(futureResult) shouldBe "text"
  }

  it should "produce an error if the param does not exist" in {
    val request = requestFromBinaryFile("/upload.bytes")
    val futureResult: Future[String] = param("foo")(request)
    a [NotPresent] shouldBe thrownBy(Await.result(futureResult))
  }

  it should "also return query parameters" in {
    val request = requestFromBinaryFile("/upload.bytes")
    val futureResult: Future[String] = param("debug")(request)
    Await.result(futureResult) shouldBe "true"
  }

  "An OptionalMultipartParam" should "be properly parsed when it exists" in {
    val request: Request = requestFromBinaryFile("/upload.bytes")
    val futureResult: Future[Option[String]] = paramOption("type")(request)
    Await.result(futureResult) shouldBe Some("text")
  }

  it should "produce an error if the param is empty" in {
    val request: Request = requestFromBinaryFile("/upload.bytes")
    val futureResult: Future[Option[String]] = paramOption("foo")(request)
    Await.result(futureResult) shouldBe None
  }

  it should "produce an error if the param name is present but not a param" in {
    val request: Request = requestFromBinaryFile("/upload.bytes")
    val futureResult: Future[Option[String]] = paramOption("groups")(request)
    Await.result(futureResult) shouldBe None
  }

  private[this] def requestFromBinaryFile(resourceName: String): Request = {
    val s = getClass.getResourceAsStream(resourceName)
    val b = Stream.continually(s.read).takeWhile(_ != -1).map(_.toByte).toArray
    Request.decodeBytes(b)
  }
}
