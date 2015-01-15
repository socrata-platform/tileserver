package com.socrata.tileserver.exceptions

import scala.util.control.NoStackTrace

import com.rojoma.json.v3.ast.JValue

case class InvalidGeoJsonException(jValue: JValue) extends NoStackTrace {
  override val getMessage = s"Invalid geo-json: ${jValue.toString}"
}
