package io.finch.petstore

import com.twitter.finagle.Service
import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.util.{Await, Future}
import io.finch.argonaut._
import io.finch.petstore.endpoint
import io.finch.request._
import io.finch.response._
import io.finch.route._

/**
 * Provides the paths and endpoints for all the API's public service methods.
 */
object endpoint extends ErrorHandling {

  /**
   * Private method that compiles all pet service endpoints.
   * @param db The petstore database.
   * @return Bundled compilation of all pet service endpoints.
   */
  private def petEndpts(db: PetstoreDb) = getPetEndpt(db) :+: addPetEndpt(db) :+: updatePetEndpt(db) :+:
      getPetsByStatusEndpt(db) :+: findPetsByTagEndpt(db) :+: deletePetEndpt(db) :+: updatePetViaFormEndpt(db) :+:
      uploadImageEndpt(db)

  /**
   * Private method that compiles all store service endpoints.
   * @param db The petstore database.
   * @return Bundled compilation of all store service endpoints.
   */
  private def storeEndpts(db: PetstoreDb) = getInventoryEndpt(db) :+: addOrderEndpt(db) :+: deleteOrderEndpt(db) :+:
      findOrderEndpt(db)

  /**
   * Private method that compiles all user service endpoints.
   * @param db The petstore database.
   * @return Bundled compilation of all user service endpoints.
   */
  private def userEndpts(db: PetstoreDb) = addUserEndpt(db) :+: addUsersViaList(db) :+: addUsersViaArray(db) :+:
      getUserEndpt(db) :+: updateUserEndpt(db)

  /**
   * Compiles together all the endpoints relating to public service methods.
   * @param db The petstore database.
   * @return A service that contains all provided endpoints of the API.
   */
  def makeService(db: PetstoreDb): Service[Request, Response] = handleExceptions andThen (
    petEndpts(db) :+:
    storeEndpts(db) :+:
    userEndpts(db)
  ).toService


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
  def updatePetEndpt(db: PetstoreDb): Router[RequestReader[Pet]] = Put / "pet" /> {
    val rr: RequestReader[Pet] = body.as[Pet].embedFlatMap { pet =>
      val f: Future[Pet] = for {
        _ <- db.updatePet(pet.copy(id = Some(pet.id.getOrElse(-1))))
        newPet <- db.getPet(pet.id.getOrElse(-1))
      } yield newPet
      f
    }
    rr
  }

//  /**
//   * Endpoint for the get allPets service method.
//   * @param db The petstore database.
//   * @return A Router that contains a sequence of all the Pets in the database.
//   */
//  def getAllPetsEndpt(db: PetstoreDb): Router[Seq[Pet]] = Get / "pet" / "all" /> {
//    Await.result(db.allPets)
//  }

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
   * @param db The petstore database.
   * @return A Router that contains a RequestReader of the sequence of all Pets with the given Tags.
   */
  def findPetsByTagEndpt(db: PetstoreDb): Router[RequestReader[Seq[Pet]]] = Get / "pet" / "findByTags" /> {
    reader.tagReader.embedFlatMap{
      db.findPetsByTag(_)
    }
  }

  /**
   * Endpoint for the findPetsByTag service method.
   * @param db The petstore database.
   * @return A Router that contains a RequestReader of the deletePet result (true for success, false otherwise).
   */
  def deletePetEndpt(db: PetstoreDb): Router[Future[Response]] = Delete / "pet" / long /> { petId: Long =>
    db.deletePet(petId).map(_ => NoContent())
  }

  /**
   * Endpoint for the updatePetViaForm (form data) service method.
   * @param db The petstore database.
   * @return A Router that contains a RequestReader of the Pet that was updated.
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
   * Endpoint for the uploadImage service method.
   * @param db The petstore database.
   * @return A Router that contains a RequestReader of the uploaded image's url.
   */
  def uploadImageEndpt(db: PetstoreDb): Router[RequestReader[String]] =
    Post / "pet" / long / "uploadImage" /> { petId: Long =>
      fileUpload("file").embedFlatMap { upload =>
        db.addImage(petId, upload.get())
      }
    }

  //============END PET ENDPOINTS============================================


  //+++++++++++++++STORE ENDPOINTS+++++++++++++++++++++++++++++++++++++++++++

  /**
   * Endpoint for the getInventory service method.
   * @param db The petstore database.
   * @return A Router that contains a RequestReader of a Map reflecting the inventory.
   */
  def getInventoryEndpt(db: PetstoreDb): Router[Future[Inventory]] = Get / "store" / "inventory" /> {
    db.getInventory
  }

  //  def getInventoryEndpt(db: PetstoreDb): Router[Future[Map[Status, Int]]] = Get / "store" / "inventory" /> {
//    db.getInventory
//  }

  /**
   * Endpoint for the addOrder service method.
   * @param db The petstore database.
   * @return A Router that contains a RequestReader of the autogenerated ID for the added Order.
   */
  def addOrderEndpt(db: PetstoreDb): Router[RequestReader[Long]] = Post / "store" / "order" /> {
    body.as[Order].embedFlatMap{order =>
      db.addOrder(order)
    }
  }

  /**
   * Endpoint for the deleteOrder service method.
   * @param db The petstore database.
   * @return A Router that contains a RequestReader of result of the deleteOrder method (true for success, false else).
   */
  def deleteOrderEndpt(db: PetstoreDb): Router[Future[Boolean]] = Delete / "store" / "order" / long /> {orderId: Long =>
    db.deleteOrder(orderId)
  }

  /**
   * Endpoint for the findOrder service method.
   * @param db The petstore database.
   * @return A Router that contains a RequestReader of the Order in question.
   */
  def findOrderEndpt(db: PetstoreDb): Router[Future[Order]] = Get / "store" / "order" / long /> {orderId: Long =>
    db.findOrder(orderId)
  }

  //============END STORE ENDPOINTS============================================

  //+++++++++++++++USER ENDPOINTS+++++++++++++++++++++++++++++++++++++++++++

  /**
   * Endpoint for the addUser service method.
   * @param db The petstore database.
   * @return A Router that contains a RequestReader of the username of the added User.
   */
  def addUserEndpt(db: PetstoreDb): Router[RequestReader[String]] = Post / "user" /> {
    body.as[User].embedFlatMap{newUser =>
      db.addUser(newUser)
    }
  }

  /**
   * Endpoint for the addUsersViaList service method.
   * @param db The petstore database.
   * @return A Router that contains a RequestReader of a sequence of the usernames of the Users added.
   */
  def addUsersViaList(db: PetstoreDb): Router[RequestReader[Seq[String]]] = Post / "user" / "createWithList" /> {
    body.as[Seq[User]].embedFlatMap{uList =>
      Future.collect(uList.map(db.addUser))
    }
  }

  /**
   * Endpoint for the addUsersViaList service method.
   * @param db The petstore database.
   * @return A Router that contains a RequestReader of a sequence of the usernames of the Users added.
   */
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

  /**
   * Endpoint for the getUser service method.
   * @param db The petstore database.
   * @return A Router that contains a RequestReader of the User in question.
   */
  def getUserEndpt(db: PetstoreDb): Router[Future[User]] = Get / "user" / string /> {n: String =>
    db.getUser(n)
  }

  /**
   * Endpoint for the updateUser service method.
   * @param db The petstore database.
   * @return A Router that contains a RequestReader of the User updated.
   */
  def updateUserEndpt(db: PetstoreDb): Router[RequestReader[User]] = Put / "user" / string /> {n: String =>
    body.as[User].embedFlatMap{betterUser =>
      db.updateUser(betterUser)
    }
  }

  //============END USER ENDPOINTS============================================

}

