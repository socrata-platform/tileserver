package com.socrata.tileserver.exceptions

import scala.util.control.NoStackTrace

case class FailedRenderException(message: String) extends Exception(message) with NoStackTrace
