package com.socrata.backend.errors

case class CoreServerException(message: String) extends Exception(message)
