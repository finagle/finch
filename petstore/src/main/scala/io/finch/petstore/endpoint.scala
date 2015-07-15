package io.finch.petstore

import com.twitter.finagle.Service
import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.util.{Future}
import io.finch.argonaut._
import io.finch.request._
import io.finch.response._
import io.finch.route._

/**
 * Provides the paths and endpoints for all the API's public service methods.
 */
object endpoint extends ErrorHandling {

  /**
   * Private method that compiles all pet service endpoints.
   * @return Bundled compilation of all pet service endpoints.
   */
  private def petEndpts(db: PetstoreDb) = getPetEndpt(db) :+: addPetEndpt(db) :+: updatePetEndpt(db) :+:
      getPetsByStatusEndpt(db) :+: findPetsByTagEndpt(db) :+: deletePetEndpt(db) :+: updatePetViaFormEndpt(db) :+:
      uploadImageEndpt(db)

  /**
   * Private method that compiles all store service endpoints.
   * @return Bundled compilation of all store service endpoints.
   */
  private def storeEndpts(db: PetstoreDb) = getInventoryEndpt(db) :+: addOrderEndpt(db) :+: deleteOrderEndpt(db) :+:
      findOrderEndpt(db)

  /**
   * Private method that compiles all user service endpoints.
   * @return Bundled compilation of all user service endpoints.
   */
  private def userEndpts(db: PetstoreDb) = addUserEndpt(db) :+: addUsersViaList(db) :+: addUsersViaArray(db) :+:
      getUserEndpt(db) :+: deleteUserEndpt(db) :+: updateUserEndpt(db)

  /**
   * Compiles together all the endpoints relating to public service methods.
   * @return A service that contains all provided endpoints of the API.
   */
  def makeService(db: PetstoreDb): Service[Request, Response] = handleExceptions andThen (
    petEndpts(db) :+:
    storeEndpts(db) :+:
    userEndpts(db)
  ).toService

  /**
   * The long passed in the path becomes the ID of the Pet fetched.
   * @return A Router that contains the Pet fetched.
   */
  def getPetEndpt(db: PetstoreDb): Router[Future[Pet]] = Get / "pet" / long /> {petId: Long =>
    db.getPet(petId)
  }

  /**
   * The pet to be added must be passed in the body.
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
   * The updated, better version of the current pet must be passed in the body.
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

  /**
   * The status is passed as a query parameter.
   * @return A Router that contains a RequestReader of the sequence of all Pets with the Status in question.
   */
  def getPetsByStatusEndpt(db: PetstoreDb): Router[RequestReader[Seq[Pet]]] = Get / "pet" / "findByStatus" />{
    (reader.statusReader).embedFlatMap{
      case s => db.getPetsByStatus(s)
    }
  }

  /**
   * The tags are passed as query parameters.
   * @return A Router that contains a RequestReader of the sequence of all Pets with the given Tags.
   */
  def findPetsByTagEndpt(db: PetstoreDb): Router[RequestReader[Seq[Pet]]] = Get / "pet" / "findByTags" /> {
    reader.tagReader.embedFlatMap{
      db.findPetsByTag(_)
    }
  }

  /**
   * The ID of the pet to delete is passed in the path.
   * @return A Router that contains a RequestReader of the deletePet result (true for success, false otherwise).
   */
  def deletePetEndpt(db: PetstoreDb): Router[Future[Response]] = Delete / "pet" / long /> { petId: Long =>
    db.deletePet(petId).map(_ => NoContent())
  }

  /**
   * Endpoint for the updatePetViaForm (form data) service method. The pet's ID is passed in the path.
   * @return A Router that contains a RequestReader of the Pet that was updated.
   */
  def updatePetViaFormEndpt(db: PetstoreDb): Router[RequestReader[Pet]] = Post / "pet" / long /> {petId: Long =>
    (reader.nameReader :: reader.statusReader).asTuple.embedFlatMap{
      case (n, s) =>
        for{
          pet:Pet <- db.getPet(petId)
          newPet: Pet <- db.updatePetViaForm(petId, Some(n), Some(s))
        } yield newPet
    }
  }

  /**
   * The ID of the pet corresponding to the image is passed in the path, whereas the image
   * file is passed as form data.
   * @return A Router that contains a RequestReader of the uploaded image's url.
   */
  def uploadImageEndpt(db: PetstoreDb): Router[RequestReader[String]] =
    Post / "pet" / long / "uploadImage" /> { petId: Long =>
      fileUpload("file").embedFlatMap { upload =>
        db.addImage(petId, upload.get())
      }
    }

  /**
   * @return A Router that contains a RequestReader of a Map reflecting the inventory.
   */
  def getInventoryEndpt(db: PetstoreDb): Router[Future[Inventory]] = Get / "store" / "inventory" /> {
    db.getInventory
  }

  /**
   * The order to be added is passed in the body.
   * @return A Router that contains a RequestReader of the autogenerated ID for the added Order.
   */
  def addOrderEndpt(db: PetstoreDb): Router[RequestReader[Long]] = Post / "store" / "order" /> {
    body.as[Order].embedFlatMap{order =>
      db.addOrder(order)
    }
  }

  /**
   * The ID of the order to be deleted is passed in the path.
   * @return A Router that contains a RequestReader of result of the deleteOrder method (true for success, false else).
   */
  def deleteOrderEndpt(db: PetstoreDb): Router[Future[Boolean]] = Delete / "store" / "order" / long /> {orderId: Long =>
    db.deleteOrder(orderId)
  }

  /**
   * The ID of the order to be found is passed in the path.
   * @return A Router that contains a RequestReader of the Order in question.
   */
  def findOrderEndpt(db: PetstoreDb): Router[Future[Order]] = Get / "store" / "order" / long /> {orderId: Long =>
    db.findOrder(orderId)
  }

  /**
   * The information of the added User is passed in the body.
   * @return A Router that contains a RequestReader of the username of the added User.
   */
  def addUserEndpt(db: PetstoreDb): Router[RequestReader[String]] = Post / "user" /> {
    body.as[User].embedFlatMap{newUser =>
      db.addUser(newUser)
    }
  }

  /**
   * The list of Users is passed in the body.
   * @return A Router that contains a RequestReader of a sequence of the usernames of the Users added.
   */
  def addUsersViaList(db: PetstoreDb): Router[RequestReader[Seq[String]]] = Post / "user" / "createWithList" /> {
    body.as[Seq[User]].embedFlatMap{uList =>
      Future.collect(uList.map(db.addUser))
    }
  }

  /**
   * The array of users is passed in the body.
   * @return A Router that contains a RequestReader of a sequence of the usernames of the Users added.
   */
  def addUsersViaArray(db: PetstoreDb): Router[RequestReader[Seq[String]]] = Post / "user" / "createWithArray" /> {
    body.as[Seq[User]].embedFlatMap{uList =>
      Future.collect(uList.map(db.addUser))
    }
  }

  /**
   * The username of the User to be deleted is passed in the path.
   * @return A Router that contains essentially nothing unless an error is thrown.
   */
  def deleteUserEndpt(db: PetstoreDb): Router[Future[Unit]] = Delete / "user" / string />{n: String =>
    db.deleteUser(n)
  }

  /**
   * The username of the User to be found is passed in the path.
   * @return A Router that contains the User in question.
   */
  def getUserEndpt(db: PetstoreDb): Router[Future[User]] = Get / "user" / string /> {n: String =>
    db.getUser(n)
  }

  /**
   * The username of the User to be updated is passed in the path.
   * @return A Router that contains a RequestReader of the User updated.
   */
  def updateUserEndpt(db: PetstoreDb): Router[RequestReader[User]] = Put / "user" / string /> {n: String =>
    body.as[User].embedFlatMap{betterUser =>
      db.updateUser(betterUser)
    }
  }
}

