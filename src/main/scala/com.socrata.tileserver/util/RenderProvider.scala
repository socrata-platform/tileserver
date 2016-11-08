package com.socrata.tileserver
package util

import java.io.{ByteArrayInputStream, InputStream}
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util

import com.rojoma.json.v3.ast
import com.rojoma.json.v3.ast.JNumber
import com.rojoma.json.v3.codec.JsonDecode
import com.rojoma.json.v3.util.JsonUtil
import com.rojoma.simplearm.v2.ResourceScope
import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.IOUtils
import org.slf4j.{Logger, LoggerFactory}
import org.velvia.MsgPack

import com.socrata.http.client.{HttpClient, RequestBuilder, Response}
import com.socrata.http.server.responses._

import exceptions.FailedRenderException

import RenderProvider._

/** Calls out to the renderer service to render tiles.
  *
  * @constructor create a renderer
  * @param http the http client to use.
  * @param baseUrl the base url (host, port, etc) for the service.
  */
case class RenderProvider(http: HttpClient, baseUrl: RequestBuilder) {
  /** Render the provided tile using the provided request info.
    *
    * @param rawTile contains the raw features on a tile
    * @param info the request info to use while rendering the tile.
    */
  def renderPng(rawTile: MapTile, info: RequestInfo, style: String): InputStream = {

    val content: Map[String, Any] = Map("tile" -> rawTile,
                                        "zoom" -> info.zoom,
                                        "style" -> style,
                                        "overscan" -> info.overscan.getOrElse(0))
    val packed: Array[Byte] = MsgPack.pack(content)

    val blob = info.rs.open(new ByteArrayInputStream(packed))

    val req = baseUrl.
      addPath("render").
      addHeader("X-Socrata-Federation" -> "Honey Badger").
      addHeader("X-Socrata-RequestID" -> info.requestId).
      blob(blob)

    logger.info(URLDecoder.decode(req.toString, UTF_8.name))

    val before = System.nanoTime()
    val resp = http.execute(req, info.rs)
    val after = System.nanoTime()
    val duration = (after - before)/1000000
    val message = s"Carto Renderer (${resp.resultCode}) took ${duration}ms."

    if (resp.resultCode == OK.statusCode) {
      logger.info(message)
      resp.inputStream()
    } else {
      logger.warn(message)
      throw FailedRenderException(IOUtils.toString(resp.inputStream(), UTF_8))
    }
  }
}

object RenderProvider {
  type MapTile = Map[String, Seq[Map[String, String]]]

  private val logger: Logger = LoggerFactory.getLogger(getClass)
}
