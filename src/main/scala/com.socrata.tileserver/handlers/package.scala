package com.socrata.tileserver

import com.socrata.http.server.HttpResponse
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._

import util.{GeoResponse, RequestInfo, TileEncoder}

package object handlers {
  type Handler = PartialFunction[RequestInfo, ResponseBuilder]
  type ResponseBuilder = (HttpResponse, GeoResponse) => HttpResponse

  val PbfHandler = new BaseHandler("pbf") {
    override def createResponse(reqInfo: RequestInfo,
                                base: HttpResponse,
                                encoder: TileEncoder): HttpResponse =
      base ~> ContentType("application/octet-stream") ~> ContentBytes(encoder.bytes)
  }

  val BpbfHandler = new BaseHandler("bpbf") {
    override def createResponse(reqInfo: RequestInfo,
                                base: HttpResponse,
                                encoder: TileEncoder): HttpResponse =
      base ~> Content("text/plain", encoder.base64)
  }

  val TxtHandler = new BaseHandler("txt") {
    override def createResponse(reqInfo: RequestInfo,
                                base: HttpResponse,
                                encoder: TileEncoder): HttpResponse =
      base ~> Content("text/plain", encoder.toString)
  }

  // When you try to render a png, but have no style.
  val UnfashionablePngHandler = new BaseHandler("png") {
    override def isDefinedAt(reqInfo: RequestInfo): Boolean =
      reqInfo.extension == extension && reqInfo.style.isEmpty

    override def createResponse(reqInfo: RequestInfo,
                                base: HttpResponse,
                                encoder: TileEncoder): HttpResponse =
      BadRequest ~>
        Content("text/plain", "Cannot render png without '$style' query parameter.")
  }
}
