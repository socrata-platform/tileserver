package com.socrata.tileserver
package util

import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8

import org.slf4j.{Logger, LoggerFactory}

import com.socrata.http.client.RequestBuilder
import com.socrata.http.server.util.RequestId.ReqIdHeader
import com.socrata.http.server.HttpResponse
import com.socrata.thirdparty.curator.CuratedServiceClient

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
  def doQuery(info: RequestInfo, params: Map[String, String]): GeoResponse = {
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
}
