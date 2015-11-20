package com.socrata.tileserver
package mocks

import com.rojoma.simplearm.v2.ResourceScope

import com.socrata.http.server.responses._
import com.socrata.thirdparty.geojson.FeatureJson

import UnusedSugar._
import util.GeoResponse

case class StaticGeoResponse(override val rawFeatures: Iterator[FeatureJson])
    extends GeoResponse {
  def headers(name: String): Array[String] = Array.empty

  val resultCode: Int = OK.statusCode
  val headerNames: Set[String] = Set.empty
  val payload: Array[Byte] = Array.empty
  val resourceScope: ResourceScope = Unused
}
