package com.socrata.tileserver
package util

import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8

import org.slf4j.{Logger, LoggerFactory}

import com.socrata.curator.CuratedServiceClient
import com.socrata.http.client.RequestBuilder
import com.socrata.http.server.responses._
import com.socrata.http.server.util.RequestId.ReqIdHeader

import GeoProvider._

/** Wraps calling the upstream service.
  *
  * @param client the upstream client.
  */
case class GeoProvider(client: CuratedServiceClient) {
  /** Query the upstream service.
    *
    * @param info the incoming request.
    */
  def doQuery(info: RequestInfo): GeoResponse = {
    val intersects = filter(info.tile, info.geoColumn, info.overscan.getOrElse(0))
    val params = augmentParams(info, intersects)
    val headers = HeaderFilter.headers(info.req)

    val jsonReq = { base: RequestBuilder =>
      val req = base.
        addPaths(Seq("id", s"${info.datasetId}.soqlpack")).
        addHeaders(headers).
        addHeader("X-Socrata-Federation" -> "Honey Badger").
        addHeader(ReqIdHeader -> info.requestId).
        query(params).get
      logger.info(URLDecoder.decode(req.toString, UTF_8.name))
      req
    }

    val before = System.nanoTime()
    val resp = client.execute(jsonReq, GeoResponse(_, info.rs))
    val after = System.nanoTime()
    val duration = (after - before)/1000000
    val message = s"Upstream response (${resp.resultCode}) took ${duration}ms."

    if (resp.resultCode == OK.statusCode || resp.resultCode == NotModified.statusCode) {
      logger.info(message)
    } else {
      logger.warn(message)
    }

    resp
  }
}

// scalastyle:off multiple.string.literals
object GeoProvider {
  private val logger: Logger = LoggerFactory.getLogger(getClass)
  private val selectKey = '$' + "select"
  private val whereKey = '$' + "where"
  private val groupKey = '$' + "group"
  private val styleKey = '$' + "style"
  private val overscanKey = '$' + "overscan"
  private val queryTimeoutKey = "$$" + "query_timeout_seconds"
  private val readFromNBEKey = "$$" + "read_from_nbe"
  private val versionKey = "$$" + "version"
  private val queryTimeout = config.TileServerConfig.queryTimeout

  /** Adds `where` and `select` to the parameters in `req`.
    *
    * @param info the information about this request.
    * @param filter the "$where" parameter to add.
    */
  def augmentParams(info: RequestInfo, filter: String): Map[String, String] = {
    if (info.mondaraHack) return augmentParamsForMondara(info, filter) // scalastyle:ignore

    val params = info.req.queryParameters
    val selectParam = selectKey ->
      params.get(selectKey).map(v => s"$v, ${info.geoColumn}").getOrElse(info.geoColumn)
    val whereParam = whereKey ->
      params.get(whereKey).map(v => s"($v) and ($filter)").getOrElse(filter)

    addTimeoutandVersionParameters(params + selectParam + whereParam - styleKey - overscanKey)
  }

  /** Adds `where` and `select` to the parameters in `req` for Mondara maps.
    * This is a secondary codepath to compensate for rendering very dense polygon maps.
    *
    * This should be removed once we get things working.
    *
    * @param info the information about this request.
    * @param filter the "$where" parameter to add.
    */
  def augmentParamsForMondara(info: RequestInfo, filter: String): Map[String, String] = {
    // TODO : Make `select` produce a smoother shape than it does now.
    // Returning a single instance of a shape (eg. simplify(min(info.geoColumn))
    // currently causes holes in large complex polygon datasets, so we're just
    // selecting the groupBy value for now.
    //
    // This can cause points to jitter upon zooming,
    // so we don't want to do it unless we have to.
    val select  = s"snap_to_grid(${info.geoColumn}, ${info.tile.resolution})"
    val groupBy = s"snap_to_grid(${info.geoColumn}, ${info.tile.resolution})"

    val mondaraKey = '$' + "mondara"

    val params = info.req.queryParameters
    val selectParam = selectKey ->
      params.get(selectKey).map(v => s"$v, $select").getOrElse(select)
    val whereParam = whereKey ->
      params.get(whereKey).map(v => s"($v) and ($filter)").getOrElse(filter)
    val groupParam = groupKey ->
      params.get(groupKey).map(v => s"($v), ($groupBy)").getOrElse(groupBy)

    // Using a GroupBy is necessary to avoid having holes in Mondara maps
    // without running the carto-renderer out of memory.
    // However, on very large point maps this makes loading all of the points extremely slow.
    // Thus we don't want this to be the default behavior for TileServer.
    //
    // The correct way to fix this would be to implement pagination and render
    // features ~50k at a time in the carto-renderer and then stitch those
    // images together.
    addTimeoutandVersionParameters(params + selectParam + whereParam + groupParam - styleKey - overscanKey - mondaraKey)
  }

  /** Return the SoQL fragment for the $where parameter.
    *
    * @param tile the QuadTile we're filtering for.
    * @param geoColumn the column to match against.
    * @param overscan the amount of overscan in pixels
    */
  def filter(tile: QuadTile, geoColumn: String, overscan: Int): String = {
    val corners = tile.corners(overscan).map { case (lat, lon) => s"$lat $lon" }.mkString(",")
    s"intersects($geoColumn, 'MULTIPOLYGON((($corners)))')"
  }

  /**
    *
    * @param params query parameters
    * @return query parameters + query_tieout_seconds=300 + read_from_nbe=true + version=2.1
    * This function adds three parameters to the query:
    * query_timeout_seconds, read_from_nbe, and version
    * read_from_nbe and version were added as part of
    * this epic: https://socrata.atlassian.net/browse/EN-12365
    * This change ensures that we get the correct data for building maps for derived views.
    *
    */
  def addTimeoutandVersionParameters(params: Map[String, String]): Map[String, String] = {
    val queryTimeoutParam = queryTimeoutKey -> queryTimeout.toString
    val readFromNbeParam  = readFromNBEKey -> "true"
    val versionParam = versionKey -> "2.1"
    params + queryTimeoutParam + readFromNbeParam + versionParam
  }
}
