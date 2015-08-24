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

case class CartoRenderer(http: HttpClient, baseUrl: RequestBuilder) {
  // TODO: Use RequestInfo
  def renderPng(pbf: String, info: RequestInfo): InputStream = {
    val style = info.style.get
    val content = json"{ bpbf: ${pbf}, zoom: ${info.zoom}, style: ${style} }"
    val req = baseUrl.
      addPath("render").
      addHeader("X-Socrata-RequestID" -> info.requestId).
      jsonBody(content)

    logger.info(content.toString)

    handleResponse(http.execute(req, info.rs))
  }
}

object CartoRenderer {
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private[util] def handleResponse(resp: Response): InputStream = {
    if (resp.resultCode == ScOk) {
      resp.inputStream()
    } else {
        throw FailedRenderException(IOUtils.toString(resp.inputStream(), UTF_8))
    }
  }
}
