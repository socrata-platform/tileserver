package com.socrata.backend.errors

/** Zookeeper Lookup Failed. */
case class ServiceDiscoveryException(message: String) extends Exception(message)
