package com.socrata.tileserver.util

import com.rojoma.simplearm.v2.ResourceScope

import com.socrata.http.server.util.RequestId.{RequestId, getFromRequest}
import com.socrata.http.server.HttpRequest

case class RequestInfo(req: HttpRequest,
                       datasetId: String,
                       geoColumn: String,
                       tile: QuadTile,
                       extension: String) {
  val requestId: RequestId = getFromRequest(req.servletRequest)
  val style: Option[String] = req.queryParameters.get("$style")
  val rs: ResourceScope = req.resourceScope
  val zoom: Int = tile.zoom
}
