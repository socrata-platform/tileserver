package com.socrata.tileserver
package services

import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponse.{SC_NOT_MODIFIED => ScNotModified}
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}
import scala.collection.JavaConverters._
import scala.util.{Try, Success, Failure}

import com.rojoma.json.v3.ast.JValue
import com.rojoma.json.v3.codec.JsonDecode.fromJValue
import com.rojoma.json.v3.codec.JsonEncode.toJValue
import com.rojoma.json.v3.conversions._
import com.rojoma.json.v3.interpolation._
import com.rojoma.json.v3.io.JsonReader
import com.rojoma.json.v3.io.JsonReaderException
import com.socrata.http.client.exceptions.ContentTypeException
import com.vividsolutions.jts.geom.GeometryFactory
import org.apache.commons.io.IOUtils
import org.slf4j.{Logger, LoggerFactory, MDC}

import com.socrata.thirdparty.curator.CuratedServiceClient
import com.socrata.http.client.{HttpClient, RequestBuilder, Response}
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.routing.{SimpleResource, TypedPathComponent}
import com.socrata.http.server.util.RequestId.{RequestId, ReqIdHeader, getFromRequest}
import com.socrata.http.server.{HttpRequest, HttpResponse, HttpService}
import com.socrata.thirdparty.geojson.{GeoJson, FeatureCollectionJson, FeatureJson}

import TileService._
import config.TileServerConfig
import exceptions.InvalidGeoJsonException
import util.TileEncoder.Feature
import util.{CoordinateMapper, HeaderFilter, QuadTile, TileEncoder}

/** Service that provides the actual tiles.
  *
  * @constructor This should only be called once, by the main application.
  * @param client The client to talk to the upstream geo-json service.
  */
case class TileService(client: CuratedServiceClient) extends SimpleResource {
  /** Type of callback we will be passing to `client`. */
  type Callback = Response => HttpResponse

  /** The types (file extensions) supported by this endpoint. */
  val types: Set[String] = Set("pbf", "bpbf", "json", "txt")

  // Call to the underlying service.
  private[services] def geoJsonQuery(requestId: RequestId,
                                     req: HttpRequest,
                                     id: String,
                                     params: Map[String, String],
                                     callback: Response => HttpResponse): HttpResponse = {
    val headers = HeaderFilter.headers(req)

    val jsonReq = { base: RequestBuilder =>
      val req = base.path(Seq("id", s"$id.geojson")).
        addHeaders(headers).
        addHeader(ReqIdHeader -> requestId).
        query(params).get
      logger.info(URLDecoder.decode(req.toString, UTF_8.name))
      req
    }

    client.execute(jsonReq, callback)
  }

  private val handleErrors: PartialFunction[Throwable, HttpResponse] = {
    case readerEx: JsonReaderException =>
      fatal("Invalid JSON returned from underlying service", readerEx)
    case geoJsonEx: InvalidGeoJsonException =>
      fatal("Invalid Geo-JSON returned from underlying service", geoJsonEx)
    case unknown =>
      fatal("Unknown error", unknown)
  }

  private[services] def processResponse(tile: QuadTile,
                                        ext: String): Callback =
  { resp: Response =>
    def createResponse(parsed: (JValue, Seq[FeatureJson])): HttpResponse = {
      val (jValue, features) = parsed
      logger.debug(s"Underlying json: ${jValue.toString}")

      val enc = TileEncoder(rollup(tile, features))
      val payload = ext match {
        case "pbf" => ContentType("application/octet-stream") ~> ContentBytes(enc.bytes)
        case "bpbf" => Content("text/plain", enc.base64)
        case "txt" => Content("text/plain", enc.toString)
        case "json" => Json(jValue)
      }

      OK ~> HeaderFilter.extract(resp) ~> payload
    }

    lazy val result = resp.resultCode match {
      case ScOk => features(resp).map(createResponse).recover(handleErrors).get
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
    val withinBox = tile.withinBox(pointColumn)

    Try {
      val params = augmentParams(req, withinBox, pointColumn)
      val requestId = extractRequestId(req)

      geoJsonQuery(requestId,
                   req,
                   identifier,
                   params,
                   processResponse(tile, ext))
    } recover {
      case e => fatal("Unknown error", e)
    } get
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

  private[services] def echoResponse(resp: Response): HttpResponse = {
    val jValue =
      Try(JsonReader.fromString(IOUtils.toString(resp.inputStream(), UTF_8)))
    val body = jValue recover {
      case e => json"""{ message: "Failed to open inputStream", cause: ${e.getMessage}}"""
    } get

    logger.info(s"Proxying response: ${resp.resultCode}: $body")

    val code = resp.resultCode
    val base = if (allowed(code)) Status(code) else InternalServerError

    base ~>
      Json(json"""{underlying: {resultCode:${resp.resultCode}, body: $body}}""")
  }

  private[services] def fatal(message: String, cause: Throwable): HttpResponse = {
    logger.warn(message, cause)

    val payload = cause match {
      case InvalidGeoJsonException(invalidJson) =>
        json"""{message: $message, invalidJson: ${invalidJson}}"""
      case e =>
        json"""{message: $message, cause: ${e.getMessage}}"""
    }

    InternalServerError ~>
      Header("Access-Control-Allow-Origin", "*") ~>
      Json(payload)
  }

  private[services] def extractRequestId(req: HttpRequest): RequestId =
    getFromRequest(req.servletRequest)

  private[services] def features(resp: Response): Try[(JValue, Seq[FeatureJson])] = {
    Try(resp.jValue(Response.acceptGeoJson)) flatMap { jValue =>
      val geoJson = GeoJson.codec.decode(jValue.toV2) collect {
        case FeatureCollectionJson(features, _) => features
      }

      geoJson map {
        features: Seq[FeatureJson] => Success(jValue -> features)
      } getOrElse {
        Failure(InvalidGeoJsonException(jValue))
      }
    }
  }

  private[services] def augmentParams(req: HttpRequest,
                                      where: String,
                                      select: String): Map[String, String] = {
    val params = req.queryParameters
    val whereParam =
      if (params.contains("$where")) params("$where") + s" and $where" else where
    val selectParam =
      if (params.contains("$select")) params("$select") + s", $select" else select

    params + ("$where" -> whereParam) + ("$select" -> selectParam)
  }

  private[services] def rollup(tile: QuadTile,
                               features: => Seq[FeatureJson]): Set[Feature] = {
    val coords = features map { f =>
      (f.geometry.getCoordinate, f.properties.mapValues(_.toV3))
    }

    val maybePixels = coords map { case (coord, props) =>
      (tile.px(coord), props)
    }

    val pixels = maybePixels collect { case (Some(px), props) =>
        (px, props)
    }

    val points = pixels groupBy { case (px, props) =>
      (geomFactory.createPoint(px), props)
    }

    val ptCounts = points map {
      case (k, v) => (k, v.size)
    }

    ptCounts.map { case ((pt, props), count) =>
      pt -> Map("count" -> toJValue(count), "properties" -> toJValue(props))
    } (collection.breakOut) // Build `Set` not `Seq`.
  }
}
