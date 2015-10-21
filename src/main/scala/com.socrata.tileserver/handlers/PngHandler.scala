package com.socrata.tileserver
package handlers

import scala.util.{Failure, Success}

import com.socrata.http.server.HttpResponse
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._

import exceptions.FailedRenderException
import util.{CartoRenderer, RequestInfo, TileEncoder}

/** Produce a png.
  *
  * @constructor Produce a handler that uses renderer.
  * @param renderer the underlying renderer.
  */
case class PngHandler(val renderer: CartoRenderer) extends BaseHandler("png") {
  override val flip = true

  override def isDefinedAt(reqInfo: RequestInfo): Boolean =
    reqInfo.extension == extension && reqInfo.style.isDefined

  override def recover: PartialFunction[Throwable, HttpResponse] = {
    case _: FailedRenderException => BadRequest ~>
        Content("text/plain", "Failed to render png; check your $style parameter.")
  }

  override def createResponse(reqInfo: RequestInfo,
                              base: HttpResponse,
                              encoder: TileEncoder): HttpResponse = {
    base ~>
      ContentType("image/png") ~>
      Stream(renderer.renderPng(encoder.wkbs, reqInfo))
  }
}
