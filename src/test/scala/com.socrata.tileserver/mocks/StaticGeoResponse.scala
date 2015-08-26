package com.socrata.tileserver
package mocks

import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}

import com.rojoma.simplearm.v2.ResourceScope

import com.socrata.thirdparty.geojson.FeatureJson

import UnusedSugar._
import util.GeoResponse

case class StaticGeoResponse(override val rawFeatures: Iterator[FeatureJson])
    extends GeoResponse {
  def headers(name: String): Array[String] = Array.empty

  val resultCode: Int = ScOk
  val headerNames: Set[String] = Set.empty
  val payload: Array[Byte] = Array.empty
  val resourceScope: ResourceScope = Unused
}
