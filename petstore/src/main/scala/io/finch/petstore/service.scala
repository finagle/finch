package io.finch.petstore

import com.twitter.finagle.Service
import com.twitter.util.Future

import io.finch._

object service{
  import model._
  import reader._

  //PET METHODS/////////////////////////////////////////////////////////
  /*
  POST: Adds a new pet to the store
   */

  /*
  PUT: Updates an existing pet
   */

  /*
  GET: Finds a pet by its status attribute
   */

  /*
  GET: Finds pets by tags
   */

  /*
  DELETE: Deletes a pet
   */

  /*
  GET: Finds a pet by its 'id'
   */

  /*
  POST: Updates a pet in the store from form data
   */

  /*
  POST: Uploads an image of a pet
   */

  //STORE METHODS/////////////////////////////////////////////////////////

  /*
  GET: Returns pet inventories of the store. Inventory is organized by the pets' status attributes
   */

  /*
  POST: Places an order for a pet
   */

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
  case class GetUser(id: Long) extends Service[AuthRequest, User]{
    def apply(req: AuthRequest): Future[User] = Db.select(id) flatMap{
      case Some(user) => user.toFuture
      case None => UserNotFound(id).toFutureException[User]
    }
  }

  /*
  GETs a user by username
   */

  /*
  POST: A REST service that creates a new user
   */

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