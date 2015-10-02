package com.socrata.tileserver
package util

import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8

import org.slf4j.{Logger, LoggerFactory}

import com.socrata.http.client.RequestBuilder
import com.socrata.http.server.util.RequestId.ReqIdHeader
import com.socrata.curator.CuratedServiceClient

import GeoProvider._

/** Wraps calling the upstream service.
  *
  * @param client the upstream client.
  */
case class GeoProvider(client: CuratedServiceClient) {
  /** Query the upstream service.
    *
    * @param info the incoming request.
    * @param params the query parameters for the request.
    */
  def doQuery(info: RequestInfo): GeoResponse = {
    val intersects = filter(info.tile, info.geoColumn)
    val params = augmentParams(info, intersects)
    val headers = HeaderFilter.headers(info.req)

    val jsonReq = { base: RequestBuilder =>
      val req = base.
        addPaths(Seq("id", s"${info.datasetId}.soqlpack")).
        addHeaders(headers).
        addHeader(ReqIdHeader -> info.requestId).
        query(params).get
      logger.info(URLDecoder.decode(req.toString, UTF_8.name))
      req
    }

    client.execute(jsonReq, GeoResponse(_, info.rs))
  }
}

object GeoProvider {
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  /** Adds `where` and `select` to the parameters in `req`.
    *
    * @param info the information about this request.
    * @param filter the "$where" parameter to add.
    * @param geoColName the "$select" parameter to add.
    */
  def augmentParams(info: RequestInfo, filter: String): Map[String, String] = {
    val simplify = s"simplify(${info.geoColumn}, ${info.tile.resolution})"

    val params = info.req.queryParameters
    val whereParam =
      if (params.contains(s"$$where")) s"""(${params(s"$$where")}) and (${filter})""" else filter
    val selectParam =
      if (params.contains(s"$$select")) s"""${params(s"$$select")}, ${simplify}""" else simplify

    params + (s"$$where" -> whereParam) + (s"$$select" -> selectParam) - s"$$style"
  }

  /** Return the SoQL fragment for the $where parameter.
    *
    * @param tile the QuadTile we're filtering for.
    * @param geoColumn the column to match against.
    */
  def filter(tile: QuadTile, geoColumn: String): String = {
    val corners = tile.corners.map { case (lat, lon) => s"${lat} ${lon}" }.mkString(",")

    s"intersects($geoColumn, 'MULTIPOLYGON(((${corners})))') " +
      s"and visible_at($geoColumn, ${tile.resolution / 2})"
  }
}
