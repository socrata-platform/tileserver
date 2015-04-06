package com.socrata.tileserver.exceptions

import scala.util.control.NoStackTrace

case class InvalidSoqlPackException(headers: Map[String, Any]) extends NoStackTrace {
  override val getMessage = {
    if (headers.isEmpty) "Unable to parse binary stream into SoQLPack/MessagePack records"
    else                 s"No geometry present or other header error: $headers"
  }
}
