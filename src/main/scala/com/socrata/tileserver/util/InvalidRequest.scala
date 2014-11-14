package com.socrata.tileserver.util

import scala.util.control.NoStackTrace

case class InvalidRequest(message: String) extends NoStackTrace
