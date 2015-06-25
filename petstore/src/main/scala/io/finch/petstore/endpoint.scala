package io.finch.petstore

import com.twitter.finagle.httpx.Method._
import io.finch.HttpRequest
import io.finch.petstore.model._
import io.finch.petstore.service._
import io.finch.route.{long, Endpoint}

//right import?

object endpoint{
  val addPet: Endpoint[HttpRequest, Pet] = Post / "pet" /> AddPet
  val updatePet: Endpoint[HttpRequest, Pet] = Put / "pet" /> UpdatePet
  val getPetsByStatus: Endpoint[HttpRequest, Seq[Pet]] = Get / "pet" / "findByStatus" /> GetPetsByStatus //tentative
  val findPetsByTag: Endpoint[HttpRequest, Pet] = Get / "pet" / "findByTags" /> FindPetsByTag
  val deletePet: Endpoint[HttpRequest, Unit] = Delete / "pet" / long /> DeletePet
  val getPet: Endpoint[HttpRequest, Pet] = Get / "pet" / long /> GetPet
  val updatePetStoreStatus: Endpoint[HttpRequest, Pet] = Post / "pet" / long /> UpdatePetStoreStatus
  val uploadImage: Endpoint[HttpRequest, Unit] = Post / "pet" / long / "uploadImage" /> UploadImage
}

