package com.socrata.tileserver
package handlers

import com.socrata.http.server.HttpResponse
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.thirdparty.geojson.GeoJson._
import com.socrata.thirdparty.geojson.{FeatureCollectionJson, GeoJsonBase}

import util.{GeoResponse, RequestInfo, TileEncoder}

/** Provide JSON passthrough for debugging/fallback. */
object JsonHandler extends Handler with FileType {
  override val extension = "json"

  override def apply(reqInfo: RequestInfo): ResponseBuilder = { (base: HttpResponse, resp) =>
      base ~> Json(FeatureCollectionJson(resp.rawFeatures.toSeq): GeoJsonBase)
  }

  override def isDefinedAt(reqInfo: RequestInfo): Boolean = reqInfo.extension == extension
}
