package com.socrata.tileserver.exceptions

case class InvalidSoqlPackException(message: String, cause: Throwable = null) // scalastyle:ignore
    extends Exception(message, cause)
