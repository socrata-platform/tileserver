package com.socrata.tileserver
package mocks

import java.nio.charset.StandardCharsets.UTF_8

import com.socrata.http.server.responses._
import com.socrata.test.common

class StaticGeoResponse(val payload: Array[Byte],
                        rc: Int = OK.statusCode,
                        headers: Map[String, Array[String]] = Map.empty,
                        ct: String = "application/json")
    extends common.mocks.StaticResponse(common.mocks.AcknowledgeableInputStream(payload),
                                        rc,
                                        headers,
                                        ct)
    with util.GeoResponse

object StaticGeoResponse {
  def apply(payload: Array[Byte],
            rc: Int,
            headers: Map[String, Array[String]],
            ct: String): StaticGeoResponse = new StaticGeoResponse(payload, rc, headers, ct)
  def apply(payload: String,
            rc: Int,
            headers: Map[String, Array[String]],
            ct: String): StaticGeoResponse = apply(payload.getBytes(UTF_8), rc, headers, ct)

  def apply(): StaticGeoResponse = new StaticGeoResponse(Array.empty)
}
