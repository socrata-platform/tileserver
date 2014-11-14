package com.socrata.tileserver.util

import scala.util.control.NoStackTrace

// For use with Try
case class InvalidRequest(message: String, info: String) extends NoStackTrace
