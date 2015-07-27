package com.socrata.tileserver
package util

import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}
import scala.util.{Failure, Try, Success}

import com.rojoma.json.v3.interpolation._
import com.rojoma.simplearm.v2.ResourceScope
import com.socrata.http.client.{HttpClient, RequestBuilder, Response}
import org.apache.commons.io.IOUtils
import org.slf4j.{Logger, LoggerFactory}

import CartoRenderer._
import exceptions.FailedRenderException

// scalastyle:off multiple.string.literals
case class CartoRenderer(http: HttpClient, baseUrl: RequestBuilder) {
  def renderPng(pbf: String,
                zoom: Int,
                cartoCss: String,
                requestId: String)(implicit rs: ResourceScope): InputStream = {
    val content = json"{ bpbf: ${pbf}, zoom: ${zoom}, style: ${cartoCss} }"
    val req = baseUrl.
      addPath("render").
      addHeader("X-Socrata-RequestID" -> requestId).
      jsonBody(content)

    http.execute(req, rs).inputStream()
  }
}

object CartoRenderer {
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  private[util] def handleResponse(response: Try[Response]): Try[InputStream] = {
    response.flatMap { resp =>
      if (resp.resultCode == ScOk) {
        Success(resp.inputStream())
      } else {
        Failure(
          FailedRenderException(IOUtils.toString(resp.inputStream(), UTF_8)))
      }
    }
  }
}
