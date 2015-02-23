/*
 * Copyright 2014, by Vladimir Kostyukov and Contributors.
 *
 * This file is a part of a Finch library that may be found at
 *
 *      https://github.com/finagle/finch
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributor(s):
 * Jens Haase
 */

package io.finch.request

import com.twitter.finagle.httpx.Request
import com.twitter.util.{Await, Future, Try}
import io.finch.HttpRequest
import org.scalatest.{FlatSpec, Matchers}
import java.nio.file.{Files, Path}

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
class MultipartParameterSpec extends FlatSpec with Matchers {

  "A RequiredMultipartFile" should "have a filename" in {
    val request = requestFromBinaryFile("/upload.bytes")
    val futureResult: Future[FileUpload] = RequiredFileUpload("groups")(request)
    Await.result(futureResult).getFilename shouldBe "dealwithit.gif"
  }

  it should "have a content type" in {
    val request = requestFromBinaryFile("/upload.bytes")
    val futureResult: Future[FileUpload] = RequiredFileUpload("groups")(request)
    Await.result(futureResult).getContentType shouldBe "image/gif"
  }

  it should "have a size greater zero" in {
    val request = requestFromBinaryFile("/upload.bytes")
    val futureResult: Future[FileUpload] = RequiredFileUpload("groups")(request)
    Await.result(futureResult).get.size should be > 0
  }

  "An OptionalMultipartFile" should "have a filename if it exists" in {
    val request = requestFromBinaryFile("/upload.bytes")
    val futureResult: Future[Option[FileUpload]] = OptionalFileUpload("groups")(request)
    Await.result(futureResult).get.getFilename shouldBe "dealwithit.gif"
  }

  "A RequiredMultipartParam" should "be properly parsed if it exists" in {
    val request = requestFromBinaryFile("/upload.bytes")
    val futureResult: Future[String] = RequiredParam("type")(request)
    Await.result(futureResult) shouldBe "text"
  }

  it should "produce an error if the param does not exist" in {
    val request = requestFromBinaryFile("/upload.bytes")
    val futureResult: Future[String] = RequiredParam("foo")(request)
    a [NotPresent] shouldBe thrownBy(Await.result(futureResult))
  }

  it should "also return query parameters" in {
    val request = requestFromBinaryFile("/upload.bytes")
    val futureResult: Future[String] = RequiredParam("debug")(request)
    Await.result(futureResult) shouldBe "true"
  }

  "An OptionalMultipartParam" should "be properly parsed when it exists" in {
    val request: HttpRequest = requestFromBinaryFile("/upload.bytes")
    val futureResult: Future[Option[String]] = OptionalParam("type")(request)
    Await.result(futureResult) shouldBe Some("text")
  }

  it should "produce an error if the param is empty" in {
    val request: HttpRequest = requestFromBinaryFile("/upload.bytes")
    val futureResult: Future[Option[String]] = OptionalParam("foo")(request)
    Await.result(futureResult) shouldBe None
  }

  private[this] def requestFromBinaryFile(resourceName: String): HttpRequest = {
    val s = getClass.getResourceAsStream(resourceName)
    val b = Stream.continually(s.read).takeWhile(_ != -1).map(_.toByte).toArray
    Request.decodeBytes(b)
  }
}
