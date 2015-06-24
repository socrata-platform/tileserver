package com.socrata.tileserver.exceptions

import scala.util.control.NoStackTrace

case class FailedRenderException(override val getMessage: String) extends NoStackTrace
