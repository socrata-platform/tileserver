package com.socrata.tileserver
package services

import java.nio.charset.StandardCharsets.UTF_8
import javax.servlet.http.HttpServletResponse

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
import util._

// scalastyle:off multiple.string.literals
/** Service that provides the actual tiles.
  *
  * @constructor This only needs to be called once, by the main application.
  * @param renderer talks to the underlying carto-renderer service.
  * @param geo talks to the upstream geo-json service.
  */
case class TileService(renderer: RenderProvider, geo: GeoProvider)  {
  // The `Handler`s that this service is backed by.
  private[this] val typedHandlers: Seq[Handler with FileType] = Seq(PbfHandler,
                                                                    BpbfHandler,
                                                                    PngHandler(renderer),
                                                                    UnfashionablePngHandler,
                                                                    JsonHandler,
                                                                    TxtHandler)
  private[this] val handler: Handler = typedHandlers.
    map(h => h: Handler).
    reduce(_.orElse(_))

  /** The types (file extensions) supported by this endpoint. */
  val types: Set[String] = typedHandlers.map(_.extension).toSet

  /** Process a request to this service.
    *
    * @param info the incoming request + metadata.
    */
  def handleRequest(info: RequestInfo) : HttpResponse = {
    try {
      val resp = geo.doQuery(info)

      val result = resp.resultCode match {
        case OK.statusCode =>
          val base = OK ~> HeaderFilter.extract(resp)

          handler(info)(base, resp)
        case NotModified.statusCode => NotModified
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

  def handleOptions() : HttpResponse = {
    Header("Access-Control-Allow-Origin", "*") ~> OK
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

        { req =>
          val info =
            RequestInfo(req, identifier, geoColumn, QuadTile(x, y, zoom), ext)
          handleRequest(info)
        }
      }

      override def options: HttpService = {
        { req =>
          handleOptions()
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
  // Basic hostname validation.
  private val validHost = """([A-Za-z0-9\-][.]?)+""".r

  /** Http response representing the underlying response.
    *
    * @param resp the underlying response.
    */
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

  /** Http response representing a fatal error.
    *
    * @param message the message for the payload.
    * @param cause the cause of the error.
    */
  def fatal(message: String, cause: Throwable): HttpResponse = {
    logger.warn(message)
    logger.warn(cause.getMessage, cause)

    @annotation.tailrec
    def rootCause(t: Throwable): Throwable =
      if (t.getCause != null) rootCause(t.getCause) else t // scalastyle:ignore

    val root = rootCause(cause)
    val payload = if (cause.getMessage != null) { // scalastyle:ignore
      json"""{message: $message, cause: ${cause.getMessage}}"""
    } else if (root.getMessage != null) { // scalastyle:ignore
      logger.warn(root.getMessage, root)
      json"""{message: $message, cause: ${root.getMessage}}"""
    } else {
      json"""{message: $message}"""
    }

    InternalServerError ~>
      Header("Access-Control-Allow-Origin", "*") ~>
      Json(payload)
  }
}
