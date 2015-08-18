package com.socrata.tileserver
package handlers

import com.socrata.http.server.HttpResponse

import util.{RequestInfo, TileEncoder}

abstract class BaseHandler(val extension: String) extends Handler {
  /** Return a ResponseBuilder for this `extension` type. */
  def apply(reqInfo: RequestInfo): ResponseBuilder = { (base: HttpResponse,
                                                        encoder: TileEncoder) =>
    createResponse(reqInfo, base, encoder)
  }

  /** Can we handle this combination of `extension` and `reqInfo`? */
  def isDefinedAt(reqInfo: RequestInfo): Boolean = reqInfo.extension == extension

  /** Recover from errors specific to this Handler. */
  def recover: PartialFunction[Throwable, HttpResponse] = PartialFunction.empty

  protected def createResponse(reqInfo: RequestInfo,
                               base: HttpResponse,
                               encoder: TileEncoder): HttpResponse
}
