package com.socrata.tileserver.exceptions

import scala.util.control.NoStackTrace

case class InvalidSoqlPackException(message: String, cause: Throwable = null) // scalastyle:ignore
    extends Exception(message, cause)
    with NoStackTrace
