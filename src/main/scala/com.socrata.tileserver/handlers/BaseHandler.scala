package com.socrata.tileserver
package handlers

import com.rojoma.json.v3.interpolation._
import org.slf4j.{Logger, LoggerFactory}

import com.socrata.http.server.HttpResponse
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._

import util.{RequestInfo, TileEncoder}

abstract class BaseHandler(val extension: String) extends Handler {
  /** Return a ResponseBuilder for this `extension` type. */
  def apply(reqInfo: RequestInfo): ResponseBuilder = { (base: HttpResponse, enc: TileEncoder) =>
    try {
      createResponse(reqInfo, base, enc)
    } catch recover
  }

  /** Can we handle this combination of `extension` and `reqInfo`? */
  def isDefinedAt(reqInfo: RequestInfo): Boolean = reqInfo.extension == extension

  /** Recover from errors specific to this Handler. */
  def recover: PartialFunction[Throwable, HttpResponse] = PartialFunction.empty

  protected def createResponse(reqInfo: RequestInfo,
                               base: HttpResponse,
                               encoder: TileEncoder): HttpResponse
}
