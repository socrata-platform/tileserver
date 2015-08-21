package com.socrata.tileserver
package handlers

import com.socrata.http.server.HttpResponse
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.thirdparty.geojson.GeoJson._
import com.socrata.thirdparty.geojson.{FeatureCollectionJson, GeoJsonBase}

import util.{GeoResponse, RequestInfo, TileEncoder}

object JsonHandler extends BaseHandler("json") {
  override def apply(reqInfo: RequestInfo): ResponseBuilder = { (base: HttpResponse, resp) =>
    try {
      base ~> Json(FeatureCollectionJson(resp.rawFeatures.toSeq): GeoJsonBase)
    } catch recover
  }

  override def createResponse(reqInfo: RequestInfo,
                              base: HttpResponse,
                              encoder: TileEncoder): HttpResponse =
    throw new Exception("Unsupported")
}
