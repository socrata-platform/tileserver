package com.socrata.tileserver.exceptions

import scala.util.control.NoStackTrace

import com.rojoma.json.v3.ast.JValue
import com.rojoma.json.v3.codec.DecodeError

case class InvalidGeoJsonException(jValue: JValue, error: DecodeError) extends NoStackTrace {
  override val getMessage = s"Unable to parse geo-json: ${error.english}, while parsing: ${jValue.toString}"
}
