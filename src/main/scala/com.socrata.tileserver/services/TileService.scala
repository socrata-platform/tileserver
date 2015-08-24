package com.socrata.tileserver
package services

import java.nio.charset.StandardCharsets.UTF_8
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponse.{SC_NOT_MODIFIED => ScNotModified}
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}

import com.rojoma.json.v3.interpolation._
import com.rojoma.json.v3.io.JsonReader
import org.slf4j.{Logger, LoggerFactory, MDC}
import org.velvia.InvalidMsgPackDataException

import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.routing.{SimpleResource, TypedPathComponent}
import com.socrata.http.server.{HttpRequest, HttpResponse, HttpService}

import TileService._
import exceptions._
import handlers._
import util.TileEncoder.Feature
import util._

// scalastyle:off multiple.string.literals
/** Service that provides the actual tiles.
  *
  * @constructor This should only be called once, by the main application.
  * @param client The client to talk to the upstream geo-json service.
  */

case class TileService(renderer: CartoRenderer, provider: GeoProvider)  {
  // The `Handler`s that this service is backed by.
  private val typedHandlers: Seq[Handler with FileType] = Seq(PbfHandler,
                                                              BpbfHandler,
                                                              PngHandler(renderer),
                                                              UnfashionablePngHandler,
                                                              JsonHandler,
                                                              TxtHandler)
  private val handler: Handler = typedHandlers.
    map(h => h: Handler).
    reduce(_.orElse(_))

  /** The types (file extensions) supported by this endpoint. */
  val types: Set[String] = typedHandlers.foldLeft(Set[String]())(_ + _.extension)

  /** Process a request to this service.
    *
    * @param req The request
    * @param identifier The dataset's identifier (aka 4x4)
    * @param geoColumn The name of the dataset column that we should be processing.
    * @param tile The QuadTile that corresponds to this zoom level.
    * @param ext The file extension that's being requested.
    */
  def handleRequest(info: RequestInfo) : HttpResponse = {
    val intersects = info.tile.intersects(info.geoColumn)

    try {
      val params = augmentParams(info.req, intersects, info.geoColumn)

      val resp = provider.doQuery(info, params)

      val result = resp.resultCode match {
        case ScOk =>
          val base = OK ~> HeaderFilter.extract(resp)

          handler(info)(base, resp)
        case ScNotModified => NotModified
        case _ => echoResponse(resp)
      }

      Header("Access-Control-Allow-Origin", "*") ~> result
    } catch {
      case packEx @ (_: InvalidSoqlPackException | _: InvalidMsgPackDataException) =>
        fatal("Invalid or corrupt data returned from underlying service", packEx)
      case unknown: Exception =>
        fatal("Unknown error", unknown)
    }
  }

  /** Handle the request.
    *
    * @param identifier unique identifier for this set
    * @param geoColumn the column in the dataset that contains the
    *                    location information.
    * @param zoom the zoom level, 1 is zoomed all the way out.
    * @param x the x coordinate of the tile.
    * @param typedY the y coordinate of the tile, and the type (extension).
    */
  def service(identifier: String,
              geoColumn: String,
              zoom: Int,
              x: Int,
              typedY: TypedPathComponent[Int]): SimpleResource =
    new SimpleResource {
      val TypedPathComponent(y, ext) = typedY

      override def get: HttpService = {
        MDC.put("X-Socrata-Resource", identifier)

        req => {
          val info =
            RequestInfo(req, identifier, geoColumn, QuadTile(x, y, zoom), ext)
          handleRequest(info)
        }
      }
    }
}

object TileService {
  private val logger: Logger = LoggerFactory.getLogger(getClass)
  private val allowed = Set(HttpServletResponse.SC_BAD_REQUEST,
                            HttpServletResponse.SC_FORBIDDEN,
                            HttpServletResponse.SC_NOT_FOUND,
                            HttpServletResponse.SC_REQUEST_TIMEOUT,
                            HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            HttpServletResponse.SC_NOT_IMPLEMENTED,
                            HttpServletResponse.SC_SERVICE_UNAVAILABLE)

  def echoResponse(resp: GeoResponse): HttpResponse = {
    val body = try {
      JsonReader.fromString(new String(resp.payload, UTF_8))
    } catch {
      case e: Exception =>
        json"""{ message: "Failed to parse underlying JSON", cause: ${e.getMessage}}"""
    }

    val code = resp.resultCode
    val base = if (allowed(code)) Status(code) else InternalServerError

    base ~>
      Json(json"""{underlying: {resultCode:${resp.resultCode}, body: $body}}""")
  }

  def fatal(message: String, cause: Throwable): HttpResponse = {
    logger.warn(message)
    logger.warn(cause.getMessage, cause.getStackTrace)

    @annotation.tailrec
    def rootCause(t: Throwable): Throwable =
      if (t.getCause != null) rootCause(t.getCause) else t // scalastyle:ignore

    val root = rootCause(cause)
    val payload = if (cause.getMessage != null) { // scalastyle:ignore
      json"""{message: $message, cause: ${cause.getMessage}}"""
    } else if (root.getMessage != null) { // scalastyle:ignore
      logger.warn(root.getMessage, root.getStackTrace)
      json"""{message: $message, cause: ${root.getMessage}}"""
    } else {
      json"""{message: $message}"""
    }

    InternalServerError ~>
      Header("Access-Control-Allow-Origin", "*") ~>
      Json(payload)
  }

  private[services] def augmentParams(req: HttpRequest,
                                      where: String,
                                      select: String): Map[String, String] = {
    val params = req.queryParameters
    val whereParam =
      if (params.contains("$where")) params("$where") + s" and $where" else where
    val selectParam =
      if (params.contains("$select")) params("$select") + s", $select" else select

    params + ("$where" -> whereParam) + ("$select" -> selectParam) - "$style"
  }
}
