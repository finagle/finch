package io.finch.petstore

import io.finch.petstore.model._
import io.finch.petstore.service._
import io.finch.petstoreDemo.AuthRequest

object endpoint{
  val getAllTags: Endpoint[AuthRequest, Seq[String]] = Get / "allTags" / long /> GetAllTags
  val deletePet: Endpoint[AuthRequest, Pet] = Delete / "pet" / long /> DeletePet
  val getAllOfCertainTag: Endpoint[AuthRequest, Seq[Pet]] = Get / "allCertainTag" / String /> GetAllOfCertainTag
  val getPet: Endpoint[AuthRequest, Pet] = Get / "pet" / long /> GetPet
  val getPetsByStatus: Endpoint[AuthRequest, Seq[Pet]] = Get / "pet" / "status" / String /> GetPetsByStatus
  val postImage: Endpoint[AuthRequest, Seq[String]] = Post / "pet" / long / "img" / String /> PostImage

}