package com.socrata.tileserver
package util

import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}

import com.rojoma.json.v3.interpolation._
import com.rojoma.simplearm.v2.ResourceScope
import com.socrata.http.client.{HttpClient, RequestBuilder, Response}
import org.apache.commons.io.IOUtils
import org.slf4j.{Logger, LoggerFactory}

import CartoRenderer._
import exceptions.FailedRenderException

/** Calls out to the renderer service to render tiles.
  *
  * @constructor create a renderer
  * @param http the http client to use.
  * @param baseUrl the base url (host, port, etc) for the service.
  */
case class CartoRenderer(http: HttpClient, baseUrl: RequestBuilder) {
  /** Render the provided tile using the provided request info.
    *
    * @param pbf the base64 encoded version of the vector tile.
    * @param info the request info to use while rendering the tile.
    */
  def renderPng(pbf: String, info: RequestInfo): InputStream = {
    val style = info.style.get
    val content = json"{ bpbf: ${pbf}, zoom: ${info.zoom}, style: ${style} }"
    val req = baseUrl.
      addPath("render").
      addHeader("X-Socrata-RequestID" -> info.requestId).
      jsonBody(content)

    logger.info(content.toString)

    val resp = http.execute(req, info.rs)

    if (resp.resultCode == ScOk) {
      resp.inputStream()
    } else {
      throw FailedRenderException(IOUtils.toString(resp.inputStream(), UTF_8))
    }
  }
}

object CartoRenderer {
  private val logger: Logger = LoggerFactory.getLogger(getClass)
}
