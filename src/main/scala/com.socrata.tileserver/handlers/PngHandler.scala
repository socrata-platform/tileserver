package com.socrata.tileserver
package handlers

import scala.util.{Failure, Success}

import com.rojoma.json.v3.interpolation._

import com.socrata.http.server.HttpResponse
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._

import exceptions.FailedRenderException
import com.socrata.tileserver.util.{CartoCssEncoder, RenderProvider, RequestInfo, TileEncoder}

/** Produce a png.
  *
  * @constructor Produce a handler that uses renderer.
  * @param renderer the underlying renderer.
  */
case class PngHandler(val renderer: RenderProvider) extends BaseHandler("png") {
  override val flip = true

  override def isDefinedAt(reqInfo: RequestInfo): Boolean =
    reqInfo.extension == extension && reqInfo.style.isDefined

  override def recover: PartialFunction[Throwable, HttpResponse] = {
    case ex: FailedRenderException => InternalServerError ~>
        Json(json"""{ message: "Failed to render png", cause: ${ex.getMessage}}""")
  }

  override def createResponse(reqInfo: RequestInfo,
                              base: HttpResponse,
                              encoder: TileEncoder): HttpResponse = {
    base ~>
      ContentType("image/png") ~>
      Stream(renderer.renderPng(encoder.wkbsAndAttributes, reqInfo, CartoCssEncoder(reqInfo).cartoCss))
  }
}
