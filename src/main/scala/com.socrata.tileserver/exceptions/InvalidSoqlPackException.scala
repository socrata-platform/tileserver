package com.socrata.tileserver.exceptions

/** The packed message we received was not invalid.
  *
  * @constructor create the exception.
  * @param message the exception message.
  * @param cause the cause of this exception.
  */
case class InvalidSoqlPackException(message: String, cause: Throwable = null) // scalastyle:ignore
    extends Exception(message, cause)
