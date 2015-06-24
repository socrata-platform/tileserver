package com.socrata.tileserver
package util

import java.io.InputStream
import java.nio.charset.StandardCharsets.UTF_8
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}
import scala.util.{Failure, Try, Success}

import com.rojoma.json.v3.io.JValueEventIterator
import com.rojoma.json.v3.interpolation._
import com.rojoma.simplearm.v2.ResourceScope
import com.socrata.http.client.{HttpClient, JsonHttpRequest, RequestBuilder, Response}
import org.apache.commons.io.IOUtils

import CartoRenderer._
import exceptions.FailedRenderException

object CartoRenderer {
  private[util] def handleResponse(response: Try[Response]): Try[InputStream] = {
    response match {
      case Success(resp) if resp.resultCode == ScOk =>
        Success(resp.inputStream())
      case Success(resp) =>
        Failure(
          FailedRenderException(IOUtils.toString(resp.inputStream(), UTF_8)))
      case Failure(t) => Failure(t)
    }
  }
}

case class CartoRenderer(http: HttpClient, baseUrl: RequestBuilder) {
  def mapnikXml(cartoCss: String)(implicit rs: ResourceScope): Try[String] = {
    val content = JValueEventIterator(json"{ style: ${cartoCss} }") // scalastyle:ignore
    val req = new JsonHttpRequest(baseUrl.addPath("style"), content)

    Try(http.execute(req, rs)).map { resp: Response =>
      IOUtils.toString(resp.inputStream(), UTF_8)
    }
  }

  def renderPng(pbf: String,
                zoom: Int,
                cartoCss: String)(implicit rs: ResourceScope): Try[Array[Byte]]  = {
    val content = JValueEventIterator(
      json"{ bpbf: ${pbf}, zoom: ${zoom}, style: ${cartoCss} }") // scalastyle:ignore
    val req = new JsonHttpRequest(baseUrl.addPath("render"), content)

    Try(http.execute(req, rs)).map { resp: Response =>
      IOUtils.toByteArray(resp.inputStream())
    }
  }
}
