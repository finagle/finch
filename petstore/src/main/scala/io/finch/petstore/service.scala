package io.finch.petstore

import com.twitter.finagle.Service
import com.twitter.util.{Await, Future}

import io.finch._
import io.finch.petstoreDemo.{CategoryDb, TagDb, Id, PetDb}

object service{
  import io.finch.petstore.model._
  import reader._
  import io.finch.petstore._

  //begin example--these are almost the same thing

  //      val p: Future[Pet] = for {
  //        p <- petReader(req)
  //        d <- PetDb.insert(p.id.getOrElse(1230), p)
  //      } yield d
  //
  //      val d: Future[Pet] = petReader(req).flatMap { p =>
  //        PetDb.insert(p.id.getOrElse(1230), p)
  //      }

  //end example


  //PET METHODS/////////////////////////////////////////////////////////
  /*
  POST: Adds a new pet to the store
   */
  case class AddPet() extends Service[HttpRequest, Pet]{
    def apply(req: HttpRequest): Future[Pet] = {

      val newPet: Future[Pet] = for{
        petInfo <- petReader(req) //future
        petInfo.status match{
          case "available" => Store.inventory.available += 1
          case "pending" => Store.inventory.pending += 1
          case "adopted" => Store.inventory.adopted += 1
        }
      } PetDb.insert(petInfo.id, Pet(petInfo.id,
                                        petInfo.category,
                                        petInfo.name,
                                        petInfo.photoUrls,
                                        petInfo.tags,
                                        petInfo.status))
      newPet
    }
  }

  /*
  PUT: Updates an existing pet. Note that id will never be changed.
   */
  case class UpdatePet() extends Service[HttpRequest, Pet]{
    def apply(req: HttpRequest): Future[Pet] = {
      val betterPet: Future[Pet] = for{
        modInfo <- petReader(req)
        oldPetFut <- PetDb.select(modInfo.id)
        oldPet <- oldPetFut
        oldCategory <- oldPet.category
        oldStatus <- oldPet.status

        modCategory <- modInfo.category
        modStatus <- modInfo.status

        betterStatus: String = if(modStatus == None) oldStatus else modStatus

      } yield PetDb.insert(modInfo.id,
          Pet(modInfo.id,
          if(modCategory == None) Option(oldCategory) else Option(modCategory),
          if(modInfo.name == None) oldPet.name else modInfo.name,
          if(modInfo.photoUrls == None) oldPet.photoUrls else modInfo.photoUrls,
          if(modInfo.tags == None) oldPet.tags else modInfo.tags,
          Option(betterStatus)
        ))

      betterPet
    }
  }

  /*
  GET: Finds a list of pets with the specified status attribute
   */
  case class GetPetsByStatus() extends Service[HttpRequest, Seq[Pet]]{
    def apply(req: HttpRequest): Future[Seq[Pet]] = {
      val allMatches = Seq[Pet]()
      for{
        getStat <- statusReader(req)
        pList <- PetDb.all
        p <- pList
        if(p.status.equals(getStat))
      } yield allMatches :+ p //will this work? I'm assuming allMatches stays Seq[Pet] until its actually returned
    }
  }

  /*
  GET: Finds pets by tags
  Muliple tags can be provided with comma seperated strings.
   */
  case class FindPetsByTag() extends Service[HttpRequest, Seq[Pet]]{
    def apply(req: HttpRequest): Future[Seq[Pet]] = {
//      val tagStrings = tagReader(req)
      val allMatches = Seq[Pet]()
      val actualTags = Seq[Tag]()
      for{matchTags <- tagReader(req) //Seq[String]
        t <- matchTags //String
        allTags <- TagDb.all //Seq[Tag]
        singleTag <- allTags //Tag
        if(singleTag.name.equals(t))
      }yield actualTags :+ singleTag
      //actualTags should now be populated with the tags we want to match
      for{petList <- PetDb.all //List[Pet]
        p <- petList //Pet
        if (actualTags.forall(p.tags.contains)) //if actualTags is subset of p.tags
      }yield allMatches :+ p
    }
  }

  /*
  DELETE: Deletes a pet, given its ID
   */
  case class DeletePet(inputId: Long) extends Service[HttpRequest, Unit]{ //correct type?
    def apply(req: HttpRequest): Future[Unit] = {
      Future(PetDb.delete(inputId))
    }
  }

  /*
  GET: Finds a pet by its 'id'
   */
  case class GetPet(inputId: Long) extends Service[HttpRequest, Pet]{
    def apply(req: HttpRequest): Future[Pet] = {
      val tempPet:Future[Option[Pet]] = PetDb.select(inputId)
      for{
        petOpt <- tempPet //Option[Pet]
        pet <- petOpt //Pet (looks like it works...somehow)
      }yield pet
    }
  }

  //===========================NEEDS CHECKING. NAME AND STATUS ARE FORMDATA PARAMS. HOW TO GET?===========================
  /*
  POST: Updates a pet in the store from form data (only name and status can be updated)
  name and status come from form data
   */
  case class UpdatePetStoreStatus(inputId: Long) extends Service[HttpRequest, Pet]{
    def apply(req: HttpRequest): Future[Pet] = {
      val orgPetFut: Future[Option[Pet]] = PetDb.select(inputId)
      val betterPet: Future[Pet] = for{
        petOpt <- orgPetFut //Option[Pet]
        pet <- petOpt //Pet
        n <- nameReader(req) //String
        s <- statusReader(req) //String
        PetDb.delete(inputId)
      }yield PetDb.insert(inputId, Pet(inputId, pet.category, n, pet.photoUrls, pet.tags, Option(s)))
      betterPet //why is this needed? O_o
    }
  }
  //=============================================================================================================

  //============================UNFINISHED======================================================================================
  /*
  POST: Uploads an image of a pet, by adding a new url to the pet's list of images
   */
  case class UploadImage(inputId: Long, url: String) extends Service[HttpRequest, Seq[String]]{
    def apply(req: HttpRequest): Future[Seq[String]] = {
      //what is additional metadata??
      //actually not too sure how this is supposed to work....

//      val doubutsu = PetDb.select(inputId)
//      doubutsu.photoUrls :+ url
    }
  }
  //=====================================================================================================================



  //STORE METHODS/////////////////////////////////////////////////////////
  //Note that in this api there is only one store in existance.

  /*
  GET: Returns pet inventories of the store. Inventory is organized by the pets' status attributes
   */
  case class GetInventory() extends Service[HttpRequest, Store]{
    def apply(req: HttpRequest): Future[Store] = {
      //how to return a body??? what?
      //Return a list of available, adopted, and pending?

    }
  }

  /*
  POST: Places an order for a pet
   */
  case class

  /*
  DELETE: Deletes a purchase order by ID
   */

  /*
  GET: Finds a purchase order by ID
   */

  //USER METHODS/////////////////////////////////////////////////////////

  /*
  GETs a user with a certain 'id'
   */
//  case class GetUser(userId: Long) extends Service[AuthRequest, User] {
//    def apply(req: AuthRequest): Future[User] = Db.select(userId) flatMap {
//      case Some(inputUser) => inputUser.toFuture
//      case None => UserNotFound(userId).toFutureException[User]
//    }
//  }

  /*
  GETs a user by username
   */
//  case class GetUser(username: String) extends Service[AuthRequest, User] {
//    def apply(req: AuthRequest): Future[User] = {
//      var allMatch = List[User]() //empty list
//      for {id <- Db.all}
//          if (Db.select(id).username == username) allMatch.::(Db.select(id))
//      if (allMatch.length > 0) allMatch
//      else UserNotFound(id).toFutureException[User]
//    }
//  }

  /*
  POST: Creates a new user based on form information (excluding id, which is auto-generated)
   */
//  case class CreateUser(username: String, firstName: String, lastName: String,
//      email: String, password: String, phone: String) extends Service[AuthRequest, User] {
//    def apply(req: AuthRequest): Future[User] = {
//      var id = Id() //Why is this an error??
//      var newUser = User(Id(), username, firstName, lastName, email, password, phone)
//      Db.insert(id, newUser)
//    }
//  }

  /*
  POST: A REST service that creates a list of users with a given input array
   */

  /*
  POST: A REST service that creates a list of users with a given input List
   */

  /*
  GET: Logs user into the system
   */

  /*
  GET: Logs out current logged in user session
   */

  /*
  DELETE: Deletes a user
   */

  /*
  PUT: Updates a user
   */
}