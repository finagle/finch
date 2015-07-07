package io.finch.petstore

import com.twitter.util.{Await, Future}

import scala.collection.mutable
//import scala.concurrent.Await

/**
 * Provides a great majority of the service methods that allow Users to interact with the Pets in the
 * store and to get information about them.
 */
class PetstoreDb {
  private[this] val pets = mutable.Map.empty[Long, Pet]
  private[this] val tags = mutable.Map.empty[Long, Tag]
  private[this] val cat = mutable.Map.empty[Long, Category]
  private[this] val orders = mutable.Map.empty[Long, Order]
  private[this] val photos = mutable.Map.empty[Long, Array[Byte]]

  /**
   * GET: Finds a Pet object by its ID.
   *
   * @param id The ID of the [[Pet]] we're looking for
   * @return The [[Pet]] object
   */
  def getPet(id: Long): Future[Pet] = Future(
    pets.synchronized {
      pets.getOrElse(id, throw MissingPet("Your pet doesn't exist! :("))
    }
  )

  /**
   * Helper method for allPets: Allows us to check whether a given id is in the database.
   * @param id The ID of the pet in question.
   * @return true if it exists. false otherwise.
   */
  def petExists(id: Long): Future[Boolean] = Future(
    pets.contains(id)
  )

  /**
   * Helper method for addPet: Adds a tag to the tag map
   * @param inputTag The tag we want to add
   * @return The tag just added
   */
  private def addTag(inputTag: Tag): Future[Tag] =
    tags.synchronized {
      inputTag.id match{
        case x: Long => Future.exception(InvalidInput("New tag should not contain an id"))
        case _ => tags.synchronized{
          val genId = if (tags.isEmpty) 0 else tags.keys.max + 1
          tags(genId) = inputTag.copy(id = genId)
          Future(tags(genId))
        }
      }
    }

  /**
   * POST: Adds a [[Pet]] to the database, validating that the ID is empty.
   *
   * @param inputPet the new pet
   * @return the id of the new pet
   */
  def addPet(inputPet: Pet): Future[Long] =
    inputPet.id match {
      case Some(_) => Future.exception(InvalidInput("New pet should not contain an id"))
      case None => pets.synchronized {
        val id = if (pets.isEmpty) 0 else pets.keys.max + 1
        pets(id) = inputPet.copy(id = Some(id))

        inputPet.tags match{
          case Some(tagList) => tagList.map(addTag(_))
          case None => None
        }

        Future.value(id)
      }
    }

  /**
   * PUT: Updates an existing [[Pet]], while validating that a current version of
   * the [[Pet]] exists (a.k.a. an existing [[Pet]] has the same id as inputPet).
   * @param inputPet The [[Pet]] we want to replace the current [[Pet]] with. Must be passed with an ID.
   * @return The updated pet
   */
  def updatePet(inputPet: Pet): Future[Pet] = inputPet.id match {
    case Some(id) =>
      if(pets.contains(id)) pets.synchronized{
        pets(id) = inputPet
        Future.value(inputPet)
      } else {
        Future.exception(MissingPet("Invalid id: doesn't exist"))
      }
    case None => Future.exception(MissingIdentifier(s"Missing id for pet: $inputPet"))
    //    case None => Future.exception(MissingIdentifier(s"Missing id for pet: $inputPet"))
  }

  /**
   * GET: Allows the user to get all the pets in the database.
   * @return A sequence of all pets in the store.
   */
  def allPets: Future[Seq[Pet]] = Future.value(
    pets.synchronized(pets.toList.sortBy(_._1).map(_._2))
  )

  /**
   * GET: Find pets by status
   * @param s The status to filter Pets by.
   * @return A sequence of all Pets with the given status.
   */
  def getPetsByStatus(s: Status): Future[Seq[Pet]] = {
    val allMatchesFut = for{
      petList <- allPets //List[Pet]
      allBool = petList.map(_.status)
    } yield petList.filter(_.status.map(_.code.equals(s.code)).getOrElse(false))
   allMatchesFut
  }

  /**
   * GET: Find pets by tags. Multiple tags can be provided with comma-separated strings.
   * @param findTags A sequence of all the Tags we want to find matches for.
   * @return A sequence of Pets that contain all given Tags.
   */
  def findPetsByTag(findTags: Seq[String]): Future[Seq[Pet]] = {
    val matchPets = for {
      p <- pets.values
      tagList <- p.tags
      pTagStr = tagList.map(_.name)
      if(findTags.forall(pTagStr.contains))
    } yield p
    Future(matchPets.toSeq.sortBy(_.id))
  }

  /**
   * Deletes a Pet from the database.
   * @param id The ID of the Pet to be deleted.
   * @return true if deletion was successful. false otherwise.
   */
  def deletePet(id: Long): Future[Boolean] = Future.value(
    pets.synchronized {
      if (id == None) false else{
        if (pets.contains(id)) {
          pets.remove(id)
          true
        } else false
      }
    }
  )

  /**
   * POST: Update a pet in the store with form data
   * @param petId ID of the Pet to be updated.
   * @param n New name of the Pet.
   * @param s New status of the Pet.
   * @return The updated Pet.
   */
  def updatePetViaForm(petId: Long, n: Option[String], s: Option[Status]): Future[Pet] = {
      if(pets.contains(petId)) pets.synchronized{
        if (s != None) {pets(petId) = pets(petId).copy(status = s)}
        if (n != None) {pets(petId) = pets(petId).copy(name = n.get)}
        Future.value(pets(petId))
      } else Future.exception(MissingPet("Invalid id: doesn't exist"))
    }

  /**
   * POST: Upload an image.
   * @param petId The ID of the pet the image corresponds to.
   * @param data The image in byte form.
   * @return The url of the uploaded photo.
   */
  def addImage(petId: Long, data: Array[Byte]): Future[String] =
    pets.synchronized {
      for {
        pet <- getPet(petId)
        photoId = photos.synchronized {
          val nextId = if (photos.isEmpty) 0 else photos.keys.max + 1
          photos(nextId) = data
          nextId
        }
        url = s"/photos/$photoId"
        _ <- updatePet(pet.copy(photoUrls = pet.photoUrls :+ url))
      } yield url
    }

  //+++++++++++++++++++++++++++++STORE METHODS BEGIN HERE+++++++++++++++++++++++++++++++++++++++++
  /**
   * GET: Returns the current inventory.
   * @return A map of how many pets currently correspond to which Status type.
   */
  def getInventory: Future[Map[Status, Int]] = Future.value(
    pets.synchronized {
      pets.groupBy(_._2.status).flatMap {
        case (Some(status), keyVal) => Some(status -> keyVal.size)
        case (None, _) => None
      }
    }
  )

  /**
   * POST: Place an order for a pet.
   * @param order The order object to be placed with the petstore.
   * @return The autogenerated ID of the order object.
   */
  def addOrder(order: Order): Future[Long] =
    orders.synchronized {
      order.id match{
        case Some(_) => Future.exception(InvalidInput("New order should not contain an id"))
        case None => orders.synchronized{
          val genId = if (orders.isEmpty) 0 else orders.keys.max + 1
          orders(genId) = order.copy(id = Option(genId))
          Future(genId)
        }
      }
    }

  /**
   * DELETE: Delete purchase order by ID
   * @param id The ID of the order to delete.
   * @return true if deletion was successful. false otherwise.
   */
  def deleteOrder(id: Long): Future[Boolean] = Future.value(
    orders.synchronized{
      if (orders.contains(id)) {
        orders.remove(id)
        true
      } else false
    }
  )

  /**
   * GET: Find purchase order by ID
   * @param id The ID of the order to find.
   * @return The Order object in question.
   */
  def findOrder(id: Long): Future[Order] = Future.value(
    orders.synchronized{
      orders.getOrElse(id, throw OrderNotFound("Your order doesn't exist! :("))
    }
  )

  //============================STORE METHODS END HERE================================================

  //+++++++++++++++++++++++++++++USER METHODS BEGIN HERE+++++++++++++++++++++++++++++++++++++++++

  /**
   * POST: Create a User.
   * @param newGuy The User we want to add to the database.
   * @return The user name of the added User.
   */
  def addUser(newGuy: User): Future[String] =
    users.synchronized {
      val inputName: String = newGuy.username
      if(users.values.exists(_.username == inputName)) throw RedundantUsername(s"Username $inputName is already taken.")
      else{
        newGuy.id match{
          case Some(_) => Future.exception(InvalidInput("New user should not contain an id"))
          case None => users.synchronized{
            val genId = if (users.isEmpty) 0 else users.keys.max + 1
            users(genId) = newGuy.copy(id = Option(genId))
            Future(newGuy.username)
          }
        }
      }
    }

  //POST: Create list of users with given input array
  //In: List of user objects
//  def addUsersViaArray(addAll: Seq[User]): Future[Seq[String]] = {
//    val added: Seq[Future[Long]] = addAll.map(addUser)
//    val allNames: Seq[String] = addAll.map(_.username)
//    Future.value(allNames)
////    val allIds: Seq[Long] = for{
////      item: Future[Long] <- added
////      num: Long <- item
////    } yield num
////    Future.value(allIds)
//  }

  //POST: Create list of users with given input list
//  def addUsersViaList(addAll: Seq[User]): Future[Seq[String]] = {
//    addAll.map(addUser(_))
//    Future.value(addAll)

//    val added: Seq[Future[Long]] = addAll.map(addUser(_))
//    val allIds: Seq[Long] = for{
//      item: Future[Long] <- added
//      num: Long <- item
//    } yield num
//    Future.value(allIds)

//    val added: Seq[Future[Long]] = addAll.map(addUser)
//    val allNames: Seq[String] = addAll.map(_.username)
//    Future.value(allNames)
//  }

  //GET: Logs user into system
  /*
   ======  ===    ||====     ===
     ||  ||   ||  ||    || ||   ||
     ||  ||   ||  ||    || ||   ||
     ||    ===    ||====     ====
    */

  //GET: Logs out current logged in user session
  /*
   ======  ===    ||====     ===
     ||  ||   ||  ||    || ||   ||
     ||  ||   ||  ||    || ||   ||
     ||    ===    ||====     ====
    */

  //DELETE: Delete user
  /*
   ======  ===    ||====     ===
     ||  ||   ||  ||    || ||   ||
     ||  ||   ||  ||    || ||   ||
     ||    ===    ||====     ====
    */

  /**
   * GET: Get User by username, assume all usernames are unique.
   * @param name The username of the User we want to find.
   * @return The User in question.
   */
  def getUser(name: String): Future[User] = Future.value(
    users.synchronized{
      val pickMeIter: Iterable[User] = for{
        u <- users.values
        if u.username.equals(name)
      } yield u
//      pickMeIter.toSeq.take(1)
      if (pickMeIter.size == 0) throw MissingUser("This user doesn't exist!")  else pickMeIter.toSeq(0)
    }
  )

  /**
   * Helper method: Confirms that a given User exists in the database.
   * @param name The username of the User to be found.
   * @return The User.
   */
  def userExists(name: String): Future[Boolean] = Future.value(
    users.values.exists(_.username == name)
  )

  /**
   * PUT: Update User. Note that usernames cannot be changed because they are unique.
   * @param betterUser The better, updated version of the old User.
   * @return The betterUser.
   */
  def updateUser(betterUser: User): Future[User] = Future.value(
    users.synchronized{
      val realId: Long = Await.result(getUser(betterUser.username)).id.getOrElse(
        throw MissingIdentifier("This user doesn't have an id."))
      users(realId) = betterUser.copy(id = Some(realId))
      users(realId)
    }
  )

  //============================USER METHODS END HERE================================================

}

