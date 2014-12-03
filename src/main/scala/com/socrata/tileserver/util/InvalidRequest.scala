package com.socrata.tileserver.util

import scala.util.control.NoStackTrace

/**
  * For use with Try/Failure, so that we don't need to build a full stack-trace.
  *
  * @param message The message for this exception.
  * @param info any additional information that would be useful for debugging.
  */
case class InvalidRequest(message: String, info: String) extends NoStackTrace
