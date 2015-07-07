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

case class CartoRenderer(http: HttpClient, baseUrl: RequestBuilder) {
  def mapnikXml(cartoCss: String)(implicit rs: ResourceScope): Try[String] = {
    val content = json"{ style: ${cartoCss} }" // scalastyle:ignore
    val req = baseUrl.addPath("style").jsonBody(content)

    Try(http.execute(req, rs)).map { resp: Response =>
      IOUtils.toString(resp.inputStream(), UTF_8)
    }
  }

  def renderPng(pbf: String,
                zoom: Int,
                cartoCss: String)(implicit rs: ResourceScope): Try[Array[Byte]]  = {
    val content = json"{ bpbf: ${pbf}, zoom: ${zoom}, style: ${cartoCss} }" // scalastyle:ignore
    val req = baseUrl.addPath("render").jsonBody(content)

    logger.debug("content: {}", content)

    Try(http.execute(req, rs)).map { resp: Response =>
      IOUtils.toByteArray(resp.inputStream())
    }
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
