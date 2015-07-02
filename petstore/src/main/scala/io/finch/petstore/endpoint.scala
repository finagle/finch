package io.finch.petstore

import com.twitter.util.Future
import io.finch.request._
import io.finch.route.{Put, Router, long}

object endpoint{
  /*
  Writing the getPet endpoint
  -
   */


  val updatePet: Router[RequestReader[Pet]] = Put / "pet" / long /> { id : Long =>
    (reader.nameReader :: reader.statusReader).asTuple.embedFlatMap {
      case (n, s) => println(s"$n, $s, $id")
        for {
          pet <- PetstoreApp.db.getPet(id)
          _ <- PetstoreApp.db.updatePet(pet.copy(name = n, status = Some(s)))
          newPet <- PetstoreApp.db.getPet(id)
        } yield newPet
    }
  }

//  val getPetEndpt: Endpoint[HttpRequest, Pet] = Get / "pet" / long /> getPet
//  val addPetEndpt: Endpoint[HttpRequest, Pet] = Post / "pet" /> addPet
//  val updatePetEndpt: Endpoint[HttpRequest, Pet] = Put / "pet" /> updatePet
//  val getPetsByStatusEndpt: Endpoint[HttpRequest, Seq[Pet]] = Get / "pet" / "findByStatus" /> getPetsByStatus //tentative
//  val findPetsByTagEndpt: Endpoint[HttpRequest, Pet] = Get / "pet" / "findByTags" /> FindPetsByTag
//  val deletePetEndpt: Endpoint[HttpRequest, Unit] = Delete / "pet" / long /> deletePet
//  val updatePetStoreStatus: Endpoint[HttpRequest, Pet] = Post / "pet" / long /> UpdatePetStoreStatus
//  val uploadImage: Endpoint[HttpRequest, Unit] = Post / "pet" / long / "uploadImage" /> UploadImage
}

