package io.finch.petstore

import com.twitter.finagle.httpx.Response
import com.twitter.util.{Await, Future}
import io.finch.argonaut._
import io.finch.request._
import io.finch.route._

/**
 * Provides the paths and endpoints for all the API's public service methods.
 */
object endpoint{
  //+++++++++++++++PET ENDPOINTS+++++++++++++++++++++++++++++++++++++++++++
  /**
   * Endpoint for the getPet service method.
   * The long passed in the path becomes the ID of the Pet fetched.
   * @param db The petstore database.
   * @return A Router that contains the Pet fetched.
   */
  def getPetEndpt(db: PetstoreDb): Router[Future[Pet]] = Get / "pet" / long /> {petId: Long =>
    db.getPet(petId)
  }

  /**
   * Endpoint for the addPet service method.
   * @param db The petstore database.
   * @return A Router that contains a RequestReader of the ID of the Pet added.
   */
  def addPetEndpt(db: PetstoreDb): Router[RequestReader[Long]] = Post / "pet" />{
    body.as[Pet].embedFlatMap {pet =>
      val p: Future[Long] = db.addPet(pet)
      for{
        realId <- p
      }yield realId
    }
  }

  /**
   * Endpoint for the updatePet service method.
   * @param db The petstore database.
   * @return A Router that contains a RequestReader of the updated Pet.
   */
  def updatePetEndpt(db: PetstoreDb): Router[RequestReader[Pet]] = Put / "pet" / long /> { petId: Long =>
    val rr: RequestReader[Pet] = body.as[Pet].embedFlatMap { pet =>
      val f: Future[Pet] = for {
        _ <- db.updatePet(pet.copy(id = Some(petId)))
        newPet <- db.getPet(petId)
      } yield newPet
      f
    }
    rr
  }

  /**
   * Endpoint for the get allPets service method.
   * @param db The petstore database.
   * @return A Router that contains a sequence of all the Pets in the database.
   */
  def getAllPetsEndpt(db: PetstoreDb): Router[Seq[Pet]] = Get / "pet" / "all" /> {
    Await.result(db.allPets)
  }

  /**
   * Endpoint for the getPetsByStatus service method.
   * @param db The petstore database.
   * @return A Router that contains a RequestReader of the sequence of all Pets with the Status in question.
   */
  def getPetsByStatusEndpt(db: PetstoreDb): Router[RequestReader[Seq[Pet]]] = Get / "pet" / "findByStatus" />{
    (reader.statusReader).embedFlatMap{
      case s => db.getPetsByStatus(s)
    }
  }

  /**
   * Endpoint for the findPetsByTag service method.
   * @param db
   * @return
   */
  def findPetsByTagEndpt(db: PetstoreDb): Router[RequestReader[Seq[Pet]]] = Get / "pet" / "findByTags" /> {
    reader.tagReader.embedFlatMap{
      db.findPetsByTag(_)
    }
  }

  /**
   *
   * @param db
   * @return
   */
  def deletePetEndpt(db: PetstoreDb): Router[Future[Boolean]] = Delete / "pet" / long /> { petId: Long =>
    db.deletePet(petId)
  }

  /**
   *
   * @param db
   * @return
   */
  def updatePetViaFormEndpt(db: PetstoreDb): Router[RequestReader[Pet]] = Post / "pet" / long /> {petId: Long =>
    (reader.nameReader :: reader.statusReader).asTuple.embedFlatMap{
      case (n, s) =>
        for{
          pet:Pet <- db.getPet(petId)
          newPet: Pet <- db.updatePetViaForm(petId, Option(n), Option(s))
        } yield newPet
    }
  }

  /**
   *
   * @param db
   * @return
   */
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

  def addOrderEndpt(db: PetstoreDb): Router[RequestReader[Long]] = Post / "store" / "order" /> {
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

  def addUserEndpt(db: PetstoreDb): Router[RequestReader[String]] = Post / "user" /> {
    body.as[User].embedFlatMap{newUser =>
      db.addUser(newUser)
    }
  }

  def addUsersViaList(db: PetstoreDb): Router[RequestReader[Seq[String]]] = Post / "user" / "createWithList" /> {
    body.as[Seq[User]].embedFlatMap{uList =>
      Future.collect(uList.map(db.addUser))
    }
  }

  def addUsersViaArray(db: PetstoreDb): Router[RequestReader[Seq[String]]] = Post / "user" / "createWithArray" /> {
    body.as[Seq[User]].embedFlatMap{uList =>
      Future.collect(uList.map(db.addUser))
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

