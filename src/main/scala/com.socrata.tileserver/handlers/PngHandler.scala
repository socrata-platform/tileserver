package com.socrata.tileserver
package handlers

import scala.util.{Failure, Success}

import com.socrata.http.server.HttpResponse
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._

import exceptions.FailedRenderException
import util.{CartoRenderer, RequestInfo, TileEncoder}

case class PngHandler(val renderer: CartoRenderer) extends BaseHandler("png") {
  /** Can we handle this combination of `extension` and `reqInfo`? */
  override def isDefinedAt(reqInfo: RequestInfo): Boolean =
    reqInfo.extension == extension && reqInfo.style.isDefined

  /** Recover from errors specific to this Handler. */
  override def recover: PartialFunction[Throwable, HttpResponse] = {
    case _: FailedRenderException => BadRequest ~>
        Content("text/plain", "Failed to render png; check your $style parameter.")
  }

  override def createResponse(reqInfo: RequestInfo,
                              base: HttpResponse,
                              encoder: TileEncoder): HttpResponse = {
    val style = reqInfo.style.get
    val png = renderer.renderPng(encoder.base64,
                                 reqInfo.zoom,
                                 style,
                                 reqInfo.requestId)(reqInfo.rs)

    base ~> ContentType("image/png") ~> Stream(png)
  }
}
