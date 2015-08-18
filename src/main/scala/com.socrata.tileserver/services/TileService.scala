package com.socrata.tileserver
package services

import java.io.DataInputStream
import java.nio.charset.StandardCharsets.UTF_8
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponse.{SC_NOT_MODIFIED => ScNotModified}
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}
import scala.util.{Try, Success, Failure}

import com.rojoma.json.v3.ast._
import com.rojoma.json.v3.codec.JsonEncode
import com.rojoma.json.v3.interpolation._
import com.rojoma.json.v3.io.JsonReader
import com.rojoma.json.v3.io.JsonReaderException
import com.rojoma.simplearm.v2.{using, ResourceScope}
import com.vividsolutions.jts.geom.{Geometry, GeometryFactory}
import com.vividsolutions.jts.io.ParseException
import org.apache.commons.io.IOUtils
import org.slf4j.{Logger, LoggerFactory, MDC}
import org.velvia.InvalidMsgPackDataException

import com.socrata.http.client.Response
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.routing.{SimpleResource, TypedPathComponent}
import com.socrata.http.server.util.RequestId.{RequestId, getFromRequest}
import com.socrata.http.server.{HttpRequest, HttpResponse, HttpService}
import com.socrata.soql.types._
import com.socrata.soql.{SoQLPackIterator, SoQLGeoRow}
import com.socrata.thirdparty.curator.CuratedServiceClient
import com.socrata.thirdparty.geojson.GeoJson._
import com.socrata.thirdparty.geojson.{FeatureCollectionJson, FeatureJson, GeoJsonBase}

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

class TileService(renderer: CartoRenderer,
                  provider: GeoProvider) extends SimpleResource {
  /** The `Handler`s that this service is backed by. */
  val baseHandlers: Seq[BaseHandler] = Seq(PbfHandler,
                                           BpbfHandler,
                                           PngHandler(renderer),
                                           JsonHandler,
                                           TxtHandler,
                                           UnfashionablePngHandler)
  val handler: Handler = baseHandlers.map(h => h: Handler).reduce(_.orElse(_))

  /** The types (file extensions) supported by this endpoint. */
  val types: Set[String] = baseHandlers.foldLeft(Set[String]())(_ + _.extension)

  private val handleErrors: PartialFunction[Throwable, HttpResponse] = {
    case packEx @ (_: InvalidSoqlPackException | _: InvalidMsgPackDataException) =>
      fatal("Invalid or corrupt data returned from underlying service", packEx)
  }

  private[services] def processResponse(tile: QuadTile,
                                        ext: String,
                                        cartoCss: Option[String],
                                        requestId: String,
                                        rs: ResourceScope): GeoProvider.Callback = { resp: Response =>
    def createResponse(features: Iterator[FeatureJson]): HttpResponse = {
      val (rollups, raw) = features.duplicate
      val jValue = JsonEncode.toJValue(FeatureCollectionJson(raw.toSeq): GeoJsonBase)
      val enc = new TileEncoder(provider.rollup(tile, rollups), jValue)
      val base = OK ~> HeaderFilter.extract(resp)
      val info = RequestInfo(ext, requestId, tile, cartoCss, rs)
      handler(info)(base, enc)
    }

    lazy val result = resp.resultCode match {
      case ScOk =>
        val features = try {
          provider.unpackFeatures(rs)(resp)
        } catch {
          case e: Exception =>
            Failure(e)
        }

        features.
          map(createResponse).
          recover(handleErrors).
          recover { case unknown: Exception =>
            fatal("Unknown error", unknown)
          }.get
      case ScNotModified => NotModified
      case _ => echoResponse(resp)
    }

    Header("Access-Control-Allow-Origin", "*") ~> result
  }

  // Do the actual heavy lifting for the request handling.
  private[services] def handleRequest(req: HttpRequest,
                                      identifier: String,
                                      pointColumn: String,
                                      tile: QuadTile,
                                      ext: String) : HttpResponse = {
    val intersects = tile.intersects(pointColumn)

    try {
      val style = req.queryParameters.get("$style")
      val params = augmentParams(req, intersects, pointColumn)
      val requestId = extractRequestId(req)

      provider.doQuery(requestId,
                       req,
                       identifier,
                       params,
                       processResponse(tile, ext, style, requestId, req.resourceScope))
    } catch {
      case e: Exception => fatal("Unknown error", e)
    }
  }

  /** Handle the request.
    *
    * @param identifier unique identifier for this set
    * @param pointColumn the column in the dataset that contains the
    *                    location information.
    * @param zoom the zoom level, 1 is zoomed all the way out.
    * @param x the x coordinate of the tile.
    * @param typedY the y coordinate of the tile, and the type (extension).
    */
  def service(identifier: String,
              pointColumn: String,
              zoom: Int,
              x: Int,
              typedY: TypedPathComponent[Int]): SimpleResource =
    new SimpleResource {
      val TypedPathComponent(y, ext) = typedY

      override def get: HttpService = {
        MDC.put("X-Socrata-Resource", identifier)

        req => handleRequest(req, identifier, pointColumn, QuadTile(x, y, zoom), ext)
      }
    }
}

object TileService {
  private val logger: Logger = LoggerFactory.getLogger(getClass)
  private val geomFactory = new GeometryFactory()
  private val allowed = Set(HttpServletResponse.SC_BAD_REQUEST,
                            HttpServletResponse.SC_FORBIDDEN,
                            HttpServletResponse.SC_NOT_FOUND,
                            HttpServletResponse.SC_REQUEST_TIMEOUT,
                            HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            HttpServletResponse.SC_NOT_IMPLEMENTED,
                            HttpServletResponse.SC_SERVICE_UNAVAILABLE)

  def apply(renderer: CartoRenderer, provider: GeoProvider): TileService =
    new TileService(renderer, provider)

  private[services] def echoResponse(resp: Response): HttpResponse = {
    val body = try {
      JsonReader.fromString(IOUtils.toString(resp.inputStream(), UTF_8))
    } catch {
      case e: Exception =>
        json"""{ message: "Failed to open inputStream", cause: ${e.getMessage}}"""
    }

    val code = resp.resultCode
    val base = if (allowed(code)) Status(code) else InternalServerError

    base ~>
      Json(json"""{underlying: {resultCode:${resp.resultCode}, body: $body}}""")
  }

  private[services] def fatal(message: String, cause: Throwable): HttpResponse = {
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

  private[services] def extractRequestId(req: HttpRequest): RequestId =
    getFromRequest(req.servletRequest)

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
