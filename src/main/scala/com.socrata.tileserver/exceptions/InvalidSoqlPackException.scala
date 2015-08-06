package com.socrata.tileserver.exceptions

import scala.util.control.NoStackTrace

case class InvalidSoqlPackException(message: String)
    extends Exception(message)
    with NoStackTrace
