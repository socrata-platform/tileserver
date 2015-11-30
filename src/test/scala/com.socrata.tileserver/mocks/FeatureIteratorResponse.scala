package com.socrata.tileserver
package mocks

import com.socrata.http.server.responses._
import com.socrata.thirdparty.geojson.FeatureJson

class FeatureIteratorResponse(override val rawFeatures: Iterator[FeatureJson])
    extends StaticGeoResponse(Array.empty, OK.statusCode, Map.empty, "application/json")

object FeatureIteratorResponse {
  def apply(rawFeatures: Iterator[FeatureJson]): FeatureIteratorResponse =
    new FeatureIteratorResponse(rawFeatures)
}
