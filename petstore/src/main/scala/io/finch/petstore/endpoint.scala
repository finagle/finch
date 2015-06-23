package io.finch.petstore

import com.twitter.finagle.httpx.Method._
import io.finch.HttpRequest
import io.finch.petstore.model._
import io.finch.petstore.service._
import io.finch.route.Endpoint //right import?

object endpoint{
  val addPet: Endpoint[HttpRequest, Pet] = Post / "pet" /> AddPet
  val updatePet: Endpoint[HttpRequest, Pet] = Put / "pet" /> UpdatePet
  val getPetsByStatus: Endpoint[HttpRequest, Seq[Pet]] = Get / "pet" / "findByStatus" /> GetPetsByStatus //tentative


//  val getAllTags: Endpoint[AuthRequest, Seq[String]] = Get / "tags" / long /> GetAllTags
//  val deletePet: Endpoint[AuthRequest, Pet] = Delete / "pet" / long /> DeletePet
//  val getAllOfCertainTag: Endpoint[AuthRequest, Seq[Pet]] = Get / "allCertainTag" / String /> GetAllOfCertainTag
//  val getPet: Endpoint[AuthRequest, Pet] = Get / "pet" / long /> GetPet
//  val getPetsByStatus: Endpoint[AuthRequest, Seq[Pet]] = Get / "pet" / "status" / String /> GetPetsByStatus
//  val postImage: Endpoint[AuthRequest, Seq[String]] = Post / "pet" / long / "img" / String /> PostImage

}