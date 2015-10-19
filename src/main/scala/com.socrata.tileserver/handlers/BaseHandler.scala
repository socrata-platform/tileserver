package com.socrata.tileserver
package handlers

import com.socrata.http.server.HttpResponse

import util.{RequestInfo, TileEncoder}

/** Base class to simplify Handler construction.
  *
  * @param extension The file extension we are handling.
  */
abstract class BaseHandler(val extension: String) extends Handler with FileType {
  /** Return a ResponseBuilder for this `extension` type. */
  def apply(reqInfo: RequestInfo): ResponseBuilder = { (base: HttpResponse, resp) =>
    try {
      val enc = TileEncoder(resp.features(reqInfo.tile, flip))
      createResponse(reqInfo, base, enc)
    } catch recover
  }

  override def isDefinedAt(reqInfo: RequestInfo): Boolean = reqInfo.extension == extension

  /** Recover from errors specific to this handler. */
  protected def recover: PartialFunction[Throwable, HttpResponse] = PartialFunction.empty

  /** Whether or not we should flip the points (don't set this if returning a vector tile). */
  protected def flip: Boolean

  /** Create the response for this handler.
    *
    * @param reqInfo the request we're handling.
    * @param base the successful response we are building upon.
    * @param enc provides the encoded vector tile.
    */
  protected def createResponse(reqInfo: RequestInfo,
                               base: HttpResponse,
                               enc: TileEncoder): HttpResponse
}
