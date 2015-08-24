package com.socrata.tileserver.exceptions

/** Rendering failed.
  *
  * @constructor build the exception.
  * @param message the error message.
  */
case class FailedRenderException(message: String) extends Exception(message)
