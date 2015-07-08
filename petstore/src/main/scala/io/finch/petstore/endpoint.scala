package io.finch.petstore

import com.twitter.finagle.httpx.Response
import com.twitter.util.Future
import io.finch.argonaut._
import io.finch.request._
import io.finch.route._

object endpoint{
  //body.as[Pet]: RequestReader[Pet]
  //body.as[Pet].flatMap { pet => param("petId") }: RequestReader[String]
  //body.as[Pet].embedFlatMap { pet => Future(pet.id) }: RequestReader[Long]

  //+++++++++++++++PET ENDPOINTS+++++++++++++++++++++++++++++++++++++++++++
  def getPetEndpt(db: PetstoreDb): Router[Future[Pet]] = Get / "pet" / long /> {petId: Long =>
    db.getPet(petId)
  }

  def addPetEndpt(db: PetstoreDb): Router[RequestReader[Long]] = Post / "pet" />{
    body.as[Pet].embedFlatMap {pet =>
      val p: Future[Long] = db.addPet(pet)
      for{
        realId <- p
      }yield realId
    }
  }

  def updatePetEndpt(db: PetstoreDb): Router[RequestReader[Pet]] = Put / "pet" / long /> { petId: Long =>
    val rr: RequestReader[Pet] = body.as[ Pet ].embedFlatMap { pet =>
      val f: Future[Pet] = for {
        _ <- db.updatePet(pet.copy(id = Some(petId)))
        newPet <- db.getPet(petId)
      } yield newPet
      f
    }
    rr
  }

  def getPetsByStatusEndpt(db: PetstoreDb): Router[RequestReader[Seq[Pet]]] = Get / "pet" / "findByStatus" />{
    (reader.statusReader).embedFlatMap{
      case s => db.getPetsByStatus(s)
    }
  }

  def findPetsByTagEndpt(db: PetstoreDb): Router[RequestReader[Seq[Pet]]] = Get / "pet" / "findByTags" /> {
    reader.tagReader.embedFlatMap{
      db.findPetsByTag(_)
    }
  }

  def deletePetEndpt(db: PetstoreDb): Router[Future[Boolean]] = Delete / "pet" / long /> { petId: Long =>
    db.deletePet(petId)
  }

  def updatePetViaFormEndpt(db: PetstoreDb): Router[RequestReader[Pet]] = Post / "pet" / long /> {petId: Long =>
    (reader.nameReader :: reader.statusReader).asTuple.embedFlatMap{
      case (n, s) =>
        for{
          pet:Pet <- db.getPet(petId)
          newPet: Pet <- db.updatePetViaForm(petId, n, s)
        } yield newPet
    }
  }

  def uploadImageEndpt(db: PetstoreDb): Router[RequestReader[String]] =
    Post / "pet" / long / "uploadImage" /> { petId: Long =>
      fileUpload("file").embedFlatMap { upload =>
        db.addImage(petId, upload.get())
      }
    }

  //============END PET ENDPOINTS============================================


  //+++++++++++++++STORE ENDPOINTS+++++++++++++++++++++++++++++++++++++++++++

  def getInventoryEndpt(db: PetstoreDb): Router[Future[Map[Status, Int]]] = Get / "store" / "inventory" /> {
    db.getInventory
  }

  def addOrderEndpt(db: PetstoreDb): Router[RequestReader[Order]] = Post / "store" / "order" /> {
    body.as[Order].embedFlatMap{order =>
      db.addOrder(order)
    }
  }

  def deleteOrderEndpt(db: PetstoreDb): Router[Future[Boolean]] = Delete / "store" / "order" / long /> {orderId: Long =>
    db.deleteOrder(orderId)
  }

  def findOrderEndpt(db: PetstoreDb): Router[Future[Order]] = Get / "store" / "order" / long /> {orderId: Long =>
    db.findOrder(orderId)
  }

  //============END STORE ENDPOINTS============================================

  //+++++++++++++++USER ENDPOINTS+++++++++++++++++++++++++++++++++++++++++++

  def addUserEndpt(db: PetstoreDb): Router[RequestReader[User]] = Post / "user" /> {
    body.as[User].embedFlatMap{newUser =>
      db.addUser(newUser)
    }
  }

  def addUsersViaList(db: PetstoreDb) = Post / "user" / "createWithList" /> {
    body.as[Seq[User]].embedFlatMap{uList =>
      db.addUsersViaList(uList)
    }
  }

  def addUsersViaArray(db: PetstoreDb) = Post / "user" / "createWithArray" /> {
    body.as[Seq[User]].embedFlatMap { uList =>
      db.addUsersViaArray(uList)
    }
  }


  //login endpoint
  /*
 ======  ===    ||====     ===
   ||  ||   ||  ||    || ||   ||
   ||  ||   ||  ||    || ||   ||
   ||    ===    ||====     ====
  */

  //logout endpoint
  /*
 ======  ===    ||====     ===
   ||  ||   ||  ||    || ||   ||
   ||  ||   ||  ||    || ||   ||
   ||    ===    ||====     ====
  */

  //delete user endpoint
  /*
 ======  ===    ||====     ===
   ||  ||   ||  ||    || ||   ||
   ||  ||   ||  ||    || ||   ||
   ||    ===    ||====     ====
  */

  def getUserEndpt(db: PetstoreDb): Router[Future[User]] = Get / "user" / string /> {n: String =>
    db.getUser(n)
  }

  def updateUserEndpt(db: PetstoreDb): Router[RequestReader[User]] = Put / "user" / string /> {n: String =>
    body.as[User].embedFlatMap{betterUser =>
      db.updateUser(betterUser)
    }
  }

  //============END USER ENDPOINTS============================================

}

