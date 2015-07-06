package io.finch.petstore

import com.twitter.util.Future
import io.finch.argonaut._
import io.finch.request._
import io.finch.route.{Get, Put, Router, long}

object endpoint{

//  val addPetEndpt: Router[RequestReader[Pet]] = Post / "pet" /> {
//    PetstoreApp.db.addPet(reader.petReader)
//  }

  def updatePet(db: PetstoreDb): Router[RequestReader[Pet]] = Put / "pet" / long /> { petId : Long =>
    body.as[Pet].embedFlatMap { pet =>
      for {
        _ <- db.updatePet(pet.copy(id = Some(petId)))
        newPet <- db.getPet(petId)
      } yield newPet
    }
    /*(reader.nameReader :: reader.statusReader).asTuple.embedFlatMap {
      case (n, s) => println(s"$n, $s, $id")
        for {
          pet <- db.getPet(id)
          _ <- db.updatePet(pet.copy(name = n, status = Some(s)))
          newPet <- db.getPet(id)
        } yield newPet
    }*/

  }

  def getPetEndpt(db: PetstoreDb) = Get / "pet" / long /> db.getPet
//  val addPetEndpt: Endpoint[HttpRequest, Pet] = Post / "pet" /> addPet
//  val updatePetEndpt: Endpoint[HttpRequest, Pet] = Put / "pet" /> updatePet
//  val getPetsByStatusEndpt: Endpoint[HttpRequest, Seq[Pet]] = Get / "pet" / "findByStatus" /> getPetsByStatus //tentative
//  val findPetsByTagEndpt: Endpoint[HttpRequest, Pet] = Get / "pet" / "findByTags" /> FindPetsByTag
//  val deletePetEndpt: Endpoint[HttpRequest, Unit] = Delete / "pet" / long /> deletePet
//  val updatePetStoreStatus: Endpoint[HttpRequest, Pet] = Post / "pet" / long /> UpdatePetStoreStatus
//  val uploadImage: Endpoint[HttpRequest, Unit] = Post / "pet" / long / "uploadImage" /> UploadImage
}

