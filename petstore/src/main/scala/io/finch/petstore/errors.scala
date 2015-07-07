package io.finch.petstore

case class InvalidInput(message: String) extends Exception(message)
case class MissingIdentifier(message: String) extends Exception(message)
case class MissingPet(message: String) extends Exception(message)
case class MissingUser(message: String) extends Exception(message)
case class OrderNotFound(message: String) extends Exception(message)
case class RedundantUsername(message: String) extends Exception(message)
