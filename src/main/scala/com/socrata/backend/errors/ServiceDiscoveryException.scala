package com.socrata.backend.errors

case class ServiceDiscoveryException(message: String) extends Exception(message)
