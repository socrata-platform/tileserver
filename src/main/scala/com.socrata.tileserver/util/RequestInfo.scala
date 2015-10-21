package com.socrata.tileserver.util

import com.rojoma.simplearm.v2.ResourceScope

import com.socrata.http.server.util.RequestId.{RequestId, getFromRequest}
import com.socrata.http.server.HttpRequest

/** Represents the incoming request plus path components.
  *
  * @param req the incoming request
  * @param datasetId the id for the dataset (aka 4x4)
  * @param geoColumn the name of the column that contains the geometry.
  * @param tile the QuadTile representing the zoom, x, and y of the tile.
  * @param extension the file extension that is being requested.
  */
case class RequestInfo(req: HttpRequest,
                       datasetId: String,
                       geoColumn: String,
                       tile: QuadTile,
                       extension: String) {
  /** The id for this request (generated if not present). */
  val requestId: RequestId = getFromRequest(req.servletRequest)

  /** The CartoCss for the request, if present. */
  val style: Option[String] = req.queryParameters.get('$' + "style")

  /** The ResourceScope to be used when processing this request. */
  val rs: ResourceScope = req.resourceScope

  /** The zoom level of this request. */
  val zoom: Int = tile.zoom

  /** The overscan amount in pixels. */
  val overscan: Option[Int] =
    req.queryParameters.get('$' + "overscan").flatMap { s =>
      try {
        Some(s.toInt)
      } catch {
        case _: NumberFormatException => None
      }
    }

  val mondaraHack: Boolean =
    req.queryParameters.get('$' + "mondara").exists(_ == "true")
}
