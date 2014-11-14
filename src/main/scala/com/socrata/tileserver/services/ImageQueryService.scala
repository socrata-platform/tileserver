package com.socrata.tileserver
package services

import com.rojoma.json.v3.conversions.v2._
import com.rojoma.simplearm.v2.{Managed, ResourceScope}
import com.socrata.http.client.Response.ContentP
import com.socrata.http.client.{HttpClient, RequestBuilder, Response, BodylessHttpRequest}
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.routing.{SimpleResource, TypedPathComponent}
import com.socrata.http.server.{HttpRequest, HttpResponse}
import com.socrata.thirdparty.geojson.{GeoJson, FeatureCollectionJson, FeatureJson}
import com.vividsolutions.jts.geom.{Coordinate, Geometry, GeometryFactory, Point}
import java.net.URLDecoder
import javax.activation.MimeType
import no.ecc.vectortile.{VectorTileDecoder, VectorTileEncoder}
import org.slf4j.LoggerFactory
import scala.util.{Try, Success, Failure}
import util.{CoordinateMapper, ExcludedHeaders, Extensions, InvalidRequest, QuadTile}

case class ImageQueryService(http: HttpClient) extends SimpleResource {
  private val geomFactory = new GeometryFactory
  private val logger = LoggerFactory.getLogger(getClass)

  def failure(message: String, request: String = ""): HttpResponse = {
    val underlying = if (request.isEmpty) "" else s""", "request": "$request""""

    BadRequest ~>
      ContentType("application/json") ~>
      Content(s"""{"message": "$message"$underlying}""")
  }

  val types: Set[String] = Extensions.keySet

  def extractHost(req: HttpRequest): Try[(String, Option[Int])] = {
    req.header("X-Socrata-Host") match {
      case Some(h) =>
        h.split(':') match {
          case Array(host, port) => Try { (host, Some(port.toInt)) }
          case Array(host) => Success ( (host, None) )
          case _ => Failure(InvalidRequest("Invalid X-Socrata-Host header"))
        }
      case None => {
        // TODO: Log.
        Failure(InvalidRequest("Invalid X-Socrata-Host header"))
      }
    }
  }

  def geoJsonQuery(hostDef: (String, Option[Int]),
                   req: HttpRequest,
                   id: String,
                   params: Map[String, String]): (String, Response) = {
    val rs = req.resourceScope
    val headerNames = req.headerNames filterNot { s: String =>
      ExcludedHeaders(s.toLowerCase)
    }

    val headers = headerNames flatMap { name: String =>
      req.headers(name) map { (name, _) }
    } toIterable

    val (host, maybePort) = hostDef

    val builder = RequestBuilder(host).
      path(Seq("api", "id", s"$id.geojson")).
      addHeaders(headers).
      query(params)

    val jsonReq = maybePort match {
      case Some(port) => builder.port(port)
      case None => builder
    }

    (URLDecoder.decode(jsonReq.toString, "UTF-8"), http.execute(jsonReq.get, rs))
  }

  def encoder(mapper: CoordinateMapper): Response => Option[Array[Byte]] =
    response => {
      val encoder: VectorTileEncoder = new VectorTileEncoder(4096)
      val jsonp: ContentP = _ map { t =>
        t.getBaseType.startsWith("application/") && t.getBaseType.endsWith("json")
      } getOrElse false

      GeoJson.codec.decode(response.jValue(jsonp).toV2) collect {
        case FeatureCollectionJson(features, _) => {
          val coords = features map { _.geometry.getCoordinate }
          val pixels = coords map { mapper.px(_) }
          val points = pixels groupBy { geomFactory.createPoint(_) }
          val counts = points map { case (k, v) => (k, v.size) }

          counts foreach { case (pt, count) =>
            val attrs = new java.util.HashMap[String, java.lang.Integer]()
            attrs.put("count", count)
            encoder.addFeature("main", attrs, pt)
          }

          encoder.encode()
        }
      }
    }

  def addToParams(maybeParams: Option[Map[String, String]],
                  where: String,
                  select: String): Try[Map[String, String]] = {
    maybeParams match {
      case Some(params) => {
        val whereParam = if (params.contains("$where"))
          params("$where") + s"and $where" else where

        Success(params + ("$where" -> whereParam) + ("$select" -> select))
      }
      case None =>
        Failure(InvalidRequest("Malformed query string"))
    }
  }

  def service(identifier: String,
              pointColumn: String,
              z: Int,
              x: Int,
              typedY: TypedPathComponent[Int]) =
    new SimpleResource {
      val TypedPathComponent(y, ext) = typedY
      val mapper = CoordinateMapper(z)

      def handleLayer(req: HttpRequest, ext: String): HttpResponse = {
        val quadTile = QuadTile(x, y, z)
        val withinBox = quadTile.withinBox(pointColumn)

        val resp: Try[HttpResponse] = for {
          hostDef <- extractHost(req)
          params <- addToParams(req.queryParameters, withinBox, pointColumn)
        } yield {
          val (jsonReq, resp) = geoJsonQuery(hostDef, req, identifier, params)

          resp.resultCode match {
            case 200 => Extensions(ext)(encoder(mapper), resp)
            case _ => failure("underlying request failed", jsonReq)
          }
        }

        resp match {
          case Success(s) => s
          case Failure(InvalidRequest(message)) =>
            failure(message)
          case Failure(e) => {
            logger.error("Request failed for unknown reason", e)
            failure(s"Unknown error: ${e.getMessage}")
          }
        }
      }

      override def get = if (types(ext)) {
        req => handleLayer(req, ext)
      } else {
        req => failure("invalid file type")
      }
    }
}
