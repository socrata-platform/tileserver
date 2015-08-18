package com.socrata.tileserver.util

import com.rojoma.simplearm.v2.ResourceScope

import com.socrata.http.server.util.RequestId.RequestId


case class RequestInfo(extension: String,
                       requestId: RequestId,
                       tile: QuadTile,
                       style: Option[String],
                       rs: ResourceScope) {
  val zoom = tile.zoom
}
