/*
 * Copyright 2015 Vladimir Kostyukov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.finch.petstore

/**
 * The parent error from which most PetstoreAPI errors extend. Thrown whenever something in the api goes wrong.
 */
sealed abstract class PetstoreError(msg: String) extends Exception(msg) {
  def message: String
}

/**
 * Thrown when the object given is invalid (i.e. A new User or Pet contains an ID)
 * @param message An error message
 */
case class InvalidInput(message: String) extends PetstoreError(message)

/**
 * Thrown when the given object is missing a unique ID.
 * @param message An error message
 */
case class MissingIdentifier(message: String) extends PetstoreError(message)

/**
 * Thrown when a given Pet does not exist in the database.
 * @param message An error message
 */
case class MissingPet(message: String) extends PetstoreError(message)

/**
 * Thrown when the User given does not exist in the database.
 * @param message An error message
 */
case class MissingUser(message: String) extends PetstoreError(message)

/**
 * Thrown when the given Order does not exist in the database.
 * @param message An error message
 */
case class OrderNotFound(message: String) extends PetstoreError(message)

/**
 * Thrown when a new User has the same username as an existing User. (Usernames must be unique.)
 * @param message An error message
 */
case class RedundantUsername(message: String) extends PetstoreError(message)
