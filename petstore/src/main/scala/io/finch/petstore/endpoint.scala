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
  private def pets(db: PetstoreDb) =
    getPet(db) :+:
    addPet(db) :+:
    updatePet(db) :+:
    getPetsByStatus(db) :+:
    findPetsByTag(db) :+:
    deletePet(db) :+:
    updatePetViaForm(db) :+:
    uploadImage(db)

  /**
   * Private method that compiles all store service endpoints.
   * @return Bundled compilation of all store service endpoints.
   */
  private def store(db: PetstoreDb) =
    getInventory(db) :+:
    addOrder(db) :+:
    deleteOrder(db) :+:
    findOrder(db)

  /**
   * Private method that compiles all user service endpoints.
   * @return Bundled compilation of all user service endpoints.
   */
  private def users(db: PetstoreDb) =
    addUser(db) :+:
    addUsersViaList(db) :+:
    addUsersViaArray(db) :+:
    getUser(db) :+:
    deleteUser(db) :+:
    updateUser(db)

  /**
   * Compiles together all the endpoints relating to public service methods.
   * @return A service that contains all provided endpoints of the API.
   */
  def makeService(db: PetstoreDb): Service[Request, Response] = handleExceptions andThen (
    pets(db) :+:
    store(db) :+:
    users(db)
  ).toService

  /**
   * The long passed in the path becomes the ID of the Pet fetched.
   * @return A Router that contains the Pet fetched.
   */
  def getPet(db: PetstoreDb): Router[Future[Pet]] = Get / "pet" / long /> db.getPet

  /**
   * The pet to be added must be passed in the body.
   * @return A Router that contains a RequestReader of the ID of the Pet added.
   */
  def addPet(db: PetstoreDb): Router[RequestReader[Long]] = Post / "pet" /> body.as[Pet].embedFlatMap(db.addPet)

  /**
   * The updated, better version of the current pet must be passed in the body.
   * @return A Router that contains a RequestReader of the updated Pet.
   */
  def updatePet(db: PetstoreDb): Router[RequestReader[Pet]] = Put / "pet" /> {
    body.as[Pet].embedFlatMap { pet =>
      val identifier: Long = pet.id match {
        case Some(num) => num
        case None => throw MissingIdentifier("The updated pet must have a valid id.")
      }
      db.updatePet(pet.copy(id = Some(identifier)))
    }
  }

  /**
   * The status is passed as a query parameter.
   * @return A Router that contains a RequestReader of the sequence of all Pets with the Status in question.
   */
  def getPetsByStatus(db: PetstoreDb): Router[RequestReader[Seq[Pet]]] = Get / "pet" / "findByStatus" />
      reader.findByStatusReader.embedFlatMap(db.getPetsByStatus)

  /**
   * The tags are passed as query parameters.
   * @return A Router that contains a RequestReader of the sequence of all Pets with the given Tags.
   */
  def findPetsByTag(db: PetstoreDb): Router[RequestReader[Seq[Pet]]] = Get / "pet" / "findByTags" />
    reader.tagReader.embedFlatMap(db.findPetsByTag)

  /**
   * The ID of the pet to delete is passed in the path.
   * @return A Router that contains a RequestReader of the deletePet result (true for success, false otherwise).
   */
  def deletePet(db: PetstoreDb): Router[Future[Response]] = Delete / "pet" / long /> { petId: Long =>
    db.deletePet(petId).map(_ => NoContent())
  }

  /**
   * Endpoint for the updatePetViaForm (form data) service method. The pet's ID is passed in the path.
   * @return A Router that contains a RequestReader of the Pet that was updated.
   */
  def updatePetViaForm(db: PetstoreDb): Router[RequestReader[Pet]] = Post / "pet" / long /> { petId: Long =>
    (reader.nameReader :: reader.statusReader).asTuple.embedFlatMap{
      case (n, s) =>
        for {
          pet <- db.getPet(petId)
          newPet <- db.updatePetViaForm(petId, Some(n), Some(s))
        } yield newPet
    }
  }

  /**
   * The ID of the pet corresponding to the image is passed in the path, whereas the image
   * file is passed as form data.
   * @return A Router that contains a RequestReader of the uploaded image's url.
   */
  def uploadImage(db: PetstoreDb): Router[RequestReader[String]] =
    Post / "pet" / long / "uploadImage" /> { petId: Long =>
      fileUpload("file").embedFlatMap { upload =>
        db.addImage(petId, upload.get())
      }
    }

  /**
   * @return A Router that contains a RequestReader of a Map reflecting the inventory.
   */
  def getInventory(db: PetstoreDb): Router[Future[Inventory]] = Get / "store" / "inventory" /> db.getInventory

  /**
   * The order to be added is passed in the body.
   * @return A Router that contains a RequestReader of the autogenerated ID for the added Order.
   */
  def addOrder(db: PetstoreDb): Router[RequestReader[Long]] = Post / "store" / "order" />
    body.as[Order].embedFlatMap(db.addOrder)

  /**
   * The ID of the order to be deleted is passed in the path.
   * @return A Router that contains a RequestReader of result of the deleteOrder method (true for success, false else).
   */
  def deleteOrder(db: PetstoreDb): Router[Future[Boolean]] = Delete / "store" / "order" / long /> db.deleteOrder

  /**
   * The ID of the order to be found is passed in the path.
   * @return A Router that contains a RequestReader of the Order in question.
   */
  def findOrder(db: PetstoreDb): Router[Future[Order]] = Get / "store" / "order" / long /> db.findOrder


  /**
   * The information of the added User is passed in the body.
   * @return A Router that contains a RequestReader of the username of the added User.
   */
  def addUser(db: PetstoreDb): Router[RequestReader[String]] = Post / "user" /> body.as[User].embedFlatMap(db.addUser)

  /**
   * The list of Users is passed in the body.
   * @return A Router that contains a RequestReader of a sequence of the usernames of the Users added.
   */
  def addUsersViaList(db: PetstoreDb): Router[RequestReader[Seq[String]]] = Post / "user" / "createWithList" /> {
    body.as[Seq[User]].embedFlatMap { uList =>
      Future.collect(uList.map(db.addUser))
    }
  }

  /**
   * The array of users is passed in the body.
   * @return A Router that contains a RequestReader of a sequence of the usernames of the Users added.
   */
  def addUsersViaArray(db: PetstoreDb): Router[RequestReader[Seq[String]]] = Post / "user" / "createWithArray" /> {
    body.as[Seq[User]].embedFlatMap { uList =>
      Future.collect(uList.map(db.addUser))
    }
  }

  /**
   * The username of the User to be deleted is passed in the path.
   * @return A Router that contains essentially nothing unless an error is thrown.
   */
  def deleteUser(db: PetstoreDb): Router[Future[Unit]] = Delete / "user" / string /> db.deleteUser

  /**
   * The username of the User to be found is passed in the path.
   * @return A Router that contains the User in question.
   */
  def getUser(db: PetstoreDb): Router[Future[User]] = Get / "user" / string /> db.getUser

  /**
   * The username of the User to be updated is passed in the path.
   * @return A Router that contains a RequestReader of the User updated.
   */
  def updateUser(db: PetstoreDb): Router[RequestReader[User]] = Put / "user" / string /> {n: String =>
    body.as[User].embedFlatMap(db.updateUser)
  }
}

