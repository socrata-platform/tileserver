package com.socrata.tileserver
package services

import java.net.URLDecoder
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}
import scala.collection.JavaConverters._
import scala.util.{Try, Success, Failure}

import com.rojoma.json.v3.ast.JValue
import com.rojoma.json.v3.codec.JsonDecode.fromJValue
import com.rojoma.json.v3.codec.JsonEncode.toJValue
import com.rojoma.json.v3.conversions._
import com.rojoma.json.v3.interpolation._
import com.rojoma.json.v3.io.JsonReader
import com.vividsolutions.jts.geom.{Geometry, GeometryFactory}
import no.ecc.vectortile.VectorTileEncoder
import org.apache.commons.io.IOUtils
import org.slf4j.{Logger, LoggerFactory, MDC}

import com.socrata.backend.client.CoreServerClient
import com.socrata.http.client.{HttpClient, RequestBuilder, Response}
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.routing.{SimpleResource, TypedPathComponent}
import com.socrata.http.server.util.RequestId.{RequestId, ReqIdHeader, getFromRequest}
import com.socrata.http.server.{HttpRequest, HttpResponse, HttpService}
import com.socrata.thirdparty.geojson.GeoJson.codec.decode
import com.socrata.thirdparty.geojson.{GeoJson, FeatureCollectionJson, FeatureJson}

import TileService._
import config.TileServerConfig
import util._

/** Service that provides the actual tiles.
  *
  * @constructor This should only be called once, by the main application.
  * @param client The client to talk to the upstream geo-json service.
  */
case class TileService(client: CoreServerClient) extends SimpleResource {
  /** The types (file extensions) supported by this endpoint. */
  val types: Set[String] = Extensions.keySet

  private[services] def geoJsonQuery(requestId: RequestId,
                                     req: HttpRequest,
                                     id: String,
                                     params: Map[String, String],
                                     callback: Response => HttpResponse,
                                     logger: Logger =
                                       TileService.defaultLogger): HttpResponse = {
    val headerNames = req.headerNames filterNot { s: String =>
      ExcludedHeaders(s.toLowerCase)
    }

    val headers =
      headerNames flatMap { name: String =>
        req.headers(name) map { (name, _) }
      } toIterable

    val jsonReq = { base: RequestBuilder =>
      val req = base.path(Seq("id", s"$id.geojson")).
        addHeaders(headers).
        addHeader(ReqIdHeader -> requestId).
        query(params).get
      logger.info(URLDecoder.decode(req.toString, "UTF-8"))
      req
    }

    client.execute(jsonReq, callback)
  }

  private def handleRequest(req: HttpRequest,
                          identifier: String,
                          pointColumn: String,
                          tile: QuadTile,
                          ext: String): HttpResponse = {
    val mapper = tile.mapper
    val withinBox = tile.withinBox(pointColumn)

    val resp = Try {
      val params = augmentParams(req, withinBox, pointColumn)
      val requestId = extractRequestId(req)

      val callback = { resp: Response =>
        resp.resultCode match {
          case ScOk => {
            Extensions(ext)(encoder(mapper), resp)
          }
          case _ => badRequest("Underlying request failed", resp)
        }
      }

      geoJsonQuery(requestId, req, identifier, params, callback)
    }

    resp match {
      case Success(s) => s
      case Failure(InvalidRequest(message, info)) =>
        badRequest(message, info)
      case Failure(e) => {
        badRequest("Unknown error", e)
      }
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

      override def get: HttpService = if (types(ext)) {
        MDC.put("X-Socrata-Resource", identifier)

        req => handleRequest(req, identifier, pointColumn, QuadTile(x, y, zoom), ext)
      } else {
        req => badRequest("Invalid file type", ext)
      }
    }
}

object TileService {
  type Feature = (Geometry, Map[String, JValue])

  implicit val defaultLogger: Logger = LoggerFactory.getLogger(getClass)
  private val defaultTileEncoder: VectorTileEncoder = new VectorTileEncoder()
  private val geomFactory = new GeometryFactory()

  private[services] def badRequest(message: String, info: String)
                                  (implicit logger: Logger) : HttpResponse = {
    logger.warn(s"$message: $info")

    BadRequest ~>
      Header("Access-Control-Allow-Origin", "*") ~>
      Json(json"""{message: $message, info: $info}""")
  }

  private[services] def badRequest(message: String, cause: Throwable)
                                  (implicit logger: Logger): HttpResponse = {
    logger.warn(message, cause)

    BadRequest ~>
      Header("Access-Control-Allow-Origin", "*") ~>
      Json(json"""{message: $message, cause: ${cause.getMessage}}""")
  }

  private[services] def badRequest(message: String, resp: Response)
                                  (implicit logger: Logger): HttpResponse = {
    val body: JValue = JsonReader.fromString(IOUtils.toString(resp.inputStream()))
    logger.warn(s"$message: ${resp.resultCode}: $body")

    BadRequest ~>
      Header("Access-Control-Allow-Origin", "*") ~>
      Json(json"""{message: $message, resultCode:${resp.resultCode}, body: $body}""")
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

    params + ("$where" -> whereParam) + ("$select" -> selectParam)
  }

  private[services] def rollup(mapper: CoordinateMapper,
                               features: Seq[FeatureJson]): Set[Feature] = {
    val coords = features map { f =>
      (f.geometry.getCoordinate, f.properties.mapValues(_.toV3))
    }

    val pixels = coords map { case (coord, props) =>
      (mapper.tilePx(coord), props)
    }

    val points = pixels groupBy { case (px, props) =>
      (geomFactory.createPoint(px), props)
    }

    val ptCounts = points.toSeq map {
      case (k, v) => (k, v.size)
    }

    ptCounts.map { case ((pt, props), count) =>
      pt -> Map("count" -> toJValue(count), "properties" -> toJValue(props))
    } (collection.breakOut) // Build `Set` not `Seq`.
  }

  /** Returns a function that will encode a GeoJson response as a vector tile.
    *
    * @param mapper The mapper the encoder will use.
    * @param tileEnc The underlying encoder that will produce the vector tile.
    */
  private[services] def encoder(mapper: CoordinateMapper,
                                tileEncoder: VectorTileEncoder =
                                  defaultTileEncoder): Encoder = resp => {
    decode(resp.jValue(JsonP).toV2) collect {
      case FeatureCollectionJson(features, _) => {
        val rollups = rollup(mapper, features)

        rollups foreach { case (pt, attrs) =>
          tileEncoder.addFeature("main", attrs.asJava, pt)
        }

        tileEncoder.encode()
      }
    }
  }
}
