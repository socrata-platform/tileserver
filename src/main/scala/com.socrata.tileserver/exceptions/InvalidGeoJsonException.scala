package com.socrata.tileserver.exceptions

import com.rojoma.json.v3.ast.JValue
import com.rojoma.json.v3.codec.DecodeError

case class InvalidGeoJsonException(jValue: JValue, error: DecodeError)
    extends Exception(s"Unable to parse geo-json: ${error.english}, while parsing: ${jValue.toString}")
