package io.finch.petstore

case class MissingIdentifier(message: String) extends Exception(message)
case class MissingPet(message: String) extends Exception(message)
case class OrderNotFound(message: String) extends Exception(message)
case class RedundantUsername(message: String) extends Exception(message)