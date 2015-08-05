package com.socrata.tileserver
package util

import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8

import org.slf4j.{Logger, LoggerFactory}

import com.socrata.http.client.{RequestBuilder, Response}
import com.socrata.http.server.{HttpRequest, HttpResponse}
import com.socrata.http.server.util.RequestId.{RequestId, ReqIdHeader}
import com.socrata.thirdparty.curator.CuratedServiceClient

import GeoProvider._

case class GeoProvider(client: CuratedServiceClient) {
  // Call to the underlying service (Core)
  // Note: this can either pull the points as .geojson or .soqlpack
  // SoQLPack is binary protocol, much faster and more efficient than GeoJSON
  // in terms of both performance (~3x) and memory usage (1/10th, or so)
  def doQuery(requestId: RequestId,
              req: HttpRequest,
              id: String,
              params: Map[String, String],
              binaryQuery: Boolean = false,
              callback: Response => HttpResponse): HttpResponse = {
    val headers = HeaderFilter.headers(req)
    val queryType = if (binaryQuery) "soqlpack" else "geojson"

    val jsonReq = { base: RequestBuilder =>
      val req = base.path(Seq("id", s"$id.$queryType")).
        addHeaders(headers).
        addHeader(ReqIdHeader -> requestId).
        query(params).get
      logger.info(URLDecoder.decode(req.toString, UTF_8.name))
      req
    }

    client.execute(jsonReq, callback)
  }
}

object GeoProvider {
  private val logger: Logger = LoggerFactory.getLogger(getClass)
}
