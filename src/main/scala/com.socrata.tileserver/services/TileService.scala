package com.socrata.tileserver
package services

import java.io.DataInputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponse.{SC_NOT_MODIFIED => ScNotModified}
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}
import scala.util.{Try, Success, Failure}

import com.rojoma.json.v3.ast._
import com.rojoma.json.v3.codec.JsonEncode.toJValue
import com.rojoma.json.v3.interpolation._
import com.rojoma.json.v3.io.JsonReader
import com.rojoma.json.v3.io.JsonReaderException
import com.rojoma.simplearm.v2.{using, ResourceScope}
import com.vividsolutions.jts.geom.GeometryFactory
import com.vividsolutions.jts.io.ParseException
import org.apache.commons.io.IOUtils
import org.slf4j.{Logger, LoggerFactory, MDC}
import org.velvia.MsgPackUtils._
import org.velvia.{MsgPack, InvalidMsgPackDataException}

import com.socrata.thirdparty.curator.CuratedServiceClient
import com.socrata.http.client.{RequestBuilder, Response}
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.routing.{SimpleResource, TypedPathComponent}
import com.socrata.http.server.util.RequestId.{RequestId, ReqIdHeader, getFromRequest}
import com.socrata.http.server.{HttpRequest, HttpResponse, HttpService}
import com.socrata.thirdparty.geojson.{GeoJson, FeatureCollectionJson, FeatureJson}

import TileService._
import exceptions._
import util.TileEncoder.Feature
import util.{HeaderFilter, FeatureJsonIterator, QuadTile, CartoRenderer, TileEncoder}

/** Service that provides the actual tiles.
  *
  * @constructor This should only be called once, by the main application.
  * @param client The client to talk to the upstream geo-json service.
  */
case class TileService(renderer: CartoRenderer,
                       client: CuratedServiceClient) extends SimpleResource {
  /** Type of callback we will be passing to `client`. */
  type Callback = Response => HttpResponse

  /** The types (file extensions) supported by this endpoint. */
  val types: Set[String] = Set("pbf", "bpbf", "json", "txt", "png") // scalastyle:ignore

  // Call to the underlying service (Core)
  // Note: this can either pull the points as .geojson or .soqlpack
  // SoQLPack is binary protocol, much faster and more efficient than GeoJSON
  // in terms of both performance (~3x) and memory usage (1/10th, or so)
  private[services] def pointQuery(requestId: RequestId,
                                   req: HttpRequest,
                                   id: String,
                                   params: Map[String, String],
                                   binaryQuery: Boolean = false,
                                   callback: Response => HttpResponse): HttpResponse = {
    val headers = HeaderFilter.headers(req)
    val queryType = if (binaryQuery) "soqlpack" else "geojson"

    val jsonReq = { base: RequestBuilder =>
      val req = base.path(Seq("id", s"$id.$queryType")).
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
    case soqlPackEx: InvalidSoqlPackException =>
      fatal("Invalid SoQLPack returned from underlying service", soqlPackEx)
    case jtsEx: ParseException =>
      fatal("Invalid WKB geometry returned from underlying service", jtsEx)
    case unknown: Any =>
      fatal("Unknown error", unknown) // scalastyle:ignore
  }

  private[services] def processResponse(tile: QuadTile,
                                        ext: String,
                                        cartoCss: Option[String],
                                        rs: ResourceScope): Callback =
  { resp: Response =>
    def createResponse(parsed: (JValue, Iterator[FeatureJson])): HttpResponse = {
      val (jValue, features) = parsed

      logger.debug(s"Underlying json: {}", jValue)

      val enc = TileEncoder(rollup(tile, features))
      val payload = ((ext, cartoCss): @unchecked) match {
        case ("pbf", _) => ContentType("application/octet-stream") ~> ContentBytes(enc.bytes)
        case ("bpbf", _) => Content("text/plain", enc.base64) // scalastyle:ignore
        case ("txt", _) => Content("text/plain", enc.toString)
        case ("json", _) => Json(jValue)
        case ("png", Some(style)) => ContentType("image/png") ~>
            ContentBytes(renderer.renderPng(enc.toString,
                                            tile.zoom,
                                            style)(rs).get)
        case ("png", None) => fatal("Cannot render png without '$style' query parameter.")
      }

      OK ~> HeaderFilter.extract(resp) ~> payload
    }

    lazy val result = resp.resultCode match {
      case ScOk =>
        val isGeoJsonResponse = Response.acceptGeoJson(resp.contentType)
        val features = if (isGeoJsonResponse) geoJsonFeatures _ else soqlUnpackFeatures(rs)
        features(resp).map(createResponse).recover(handleErrors).get
      case ScNotModified => NotModified
      case _ => echoResponse(resp)
    }

    Header("Access-Control-Allow-Origin", "*") ~> result // scalastyle:ignore
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
      val style = params.get("$style")

      pointQuery(requestId,
                 req,
                 identifier,
                 params,
                 // Right now soqlpack queries won't work on non-geom columns
                 !req.queryParameters.contains("$select"), // scalastyle:ignore
                 processResponse(tile, ext, style, req.resourceScope))
    }.recover {
      case e: Any => fatal("Unknown error", e)
    }.get
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
    val body = jValue.recover {
      case e: Any =>
        json"""{ message: "Failed to open inputStream", cause: ${e.getMessage}}""" // scalastyle:ignore
    }.get

    logger.info(s"Proxying response: ${resp.resultCode}: $body")

    val code = resp.resultCode
    val base = if (allowed(code)) Status(code) else InternalServerError

    base ~>
      Json(json"""{underlying: {resultCode:${resp.resultCode}, body: $body}}""")
  }

  private[services] def fatal(message: String): HttpResponse =
    fatal(message, None)
  private[services] def fatal(message: String, cause: Throwable): HttpResponse =
    fatal(message, Some(cause))

  private[services] def fatal(message: String, cause: Option[Throwable]): HttpResponse = {
    logger.warn(message, cause)

    val payload = cause match {
      case Some(e: InvalidGeoJsonException) =>
        json"""{message: $message, cause: ${e.error}, invalidJson: ${e.jValue}}"""
      case Some(e) =>
        json"""{message: $message, cause: ${e.getMessage}}"""
      case None =>
        json"""{message: $message}"""
    }

    InternalServerError ~>
      Header("Access-Control-Allow-Origin", "*") ~>
      Json(payload)
  }

  private[services] def extractRequestId(req: HttpRequest): RequestId =
    getFromRequest(req.servletRequest)

  private[services] def geoJsonFeatures(resp: Response): Try[(JValue, Iterator[FeatureJson])] = {
    Try(resp.jValue(Response.acceptGeoJson)) flatMap { jValue =>
      GeoJson.codec.decode(jValue) match {
        case Left(error) => Failure(InvalidGeoJsonException(jValue, error))
        case Right(FeatureCollectionJson(features, _)) => Success(jValue -> features.toIterator)
        case Right(feature: FeatureJson) => Success(jValue -> Iterator.single(feature))
      }
    }
  }

  private[services] def soqlUnpackFeatures(rs: ResourceScope):
      Response => Try[(JValue, Iterator[FeatureJson])] = { resp: Response =>
    val dis = rs.open(new DataInputStream(resp.inputStream(Long.MaxValue)))

    try {
      val headers = MsgPack.unpack(dis, MsgPack.UNPACK_RAW_AS_STRING).asInstanceOf[Map[String, Any]]
      val jsonHeaders = JObject(headers.mapValues(v => JString(v.toString)))
      headers.asInt("geometry_index") match {
        case geomIndex: Any if geomIndex < 0 =>
          Failure(InvalidSoqlPackException(headers))
        case geomIndex: Any =>
          Success(jsonHeaders -> new FeatureJsonIterator(dis, geomIndex))
      }
    } catch {
      case _: InvalidMsgPackDataException => Failure(InvalidSoqlPackException(Map.empty))
      case _: ClassCastException =>          Failure(InvalidSoqlPackException(Map.empty))
      case _: NoSuchElementException =>      Failure(InvalidSoqlPackException(Map.empty))
      case _: NullPointerException =>        Failure(InvalidSoqlPackException(Map.empty))
    }
  }

  private[services] def augmentParams(req: HttpRequest,
                                      where: String,
                                      select: String): Map[String, String] = {
    val params = req.queryParameters
    val whereParam =
      if (params.contains("$where")) params("$where") + s" and $where" else where // scalastyle:ignore
    val selectParam =
      if (params.contains("$select")) params("$select") + s", $select" else select

    params + ("$where" -> whereParam) + ("$select" -> selectParam)
  }

  private[services] def rollup(tile: QuadTile,
                               features: => Iterator[FeatureJson]): Set[Feature] = {
    val maybePixels = features map { f =>
      (tile.px(f.geometry.getCoordinate), f.properties)
    }

    val pixels = maybePixels collect { case (Some(px), props) =>
      (px, props)
    }

    // So far, pixels is still an Iterator. pixels foreach is a streaming way to
    // do a grouping count without materializing all the pixels/geometries and producing
    // multiple intermediate objects, so we can save on memory use.
    val ptCounts = new collection.mutable.HashMap[Feature, Int].withDefaultValue(0)
    pixels foreach { case (px, props) =>
      ptCounts((geomFactory.createPoint(px), props)) += 1
    }

    ptCounts.map { case ((pt, props), count) =>
      pt -> Map("count" -> toJValue(count), "properties" -> toJValue(props))
    } (collection.breakOut) // Build `Set` not `Seq`.
  }
}
