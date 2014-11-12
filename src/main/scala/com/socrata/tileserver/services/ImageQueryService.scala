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
import org.apache.commons.io.IOUtils
import scala.collection.JavaConverters._
import util.{CoordinateMapper, QuadTile}

case class ImageQueryService(http: HttpClient) extends SimpleResource {
  private val geomFactory = new GeometryFactory

  // TODO: Make this configurable.
  val GeoJsonHost: String = "dataspace.demo.socrata.com"

  def failure(message: String, request: String = ""): HttpResponse = {
    val underlying = if (request.isEmpty) "" else s""", "request": "$request""""

    BadRequest ~>
      ContentType("application/json") ~>
      Content("""{"message": "$message"$underlying}""")
  }

  type Extension = (CoordinateMapper, Response) => HttpResponse

  val invalidJson = InternalServerError ~>
    ContentType("application/json") ~>
    Content("""{"message":"Invalid geo-json returned from underlying service."}""")

  val Pbf: Extension = (mapper, resp) => encode(mapper, resp) map {
    bytes: Array[Byte] => {
      OK ~>
        ContentType("application/octet-stream") ~>
        Header("Access-Control-Allow-Origin", "*") ~>
        ContentBytes(bytes)
    }
  } getOrElse invalidJson

  val Json: Extension = (mapper, resp) => {
    OK ~>
      ContentType("application/json") ~>
      Header("Access-Control-Allow-Origin", "*") ~>
      Stream(IOUtils.copy(resp.inputStream(), _))
  }

  val Txt: Extension = (mapper, resp) => encode(mapper, resp) map {
    bytes: Array[Byte] => {
      val decoder = new VectorTileDecoder()
      decoder.decode(bytes)

      val features = decoder.getFeatures("main").asScala map { f =>
        s"geometry: ${f.getGeometry.toString}  \tattributes: ${f.getAttributes}"
      }

      OK ~>
        ContentType("text/plain") ~>
        Content(features.mkString("\n"))
    }
  } getOrElse invalidJson

  val extensions: Map[String, Extension] = Map("pbf" -> Pbf,
                                               "txt" -> Txt,
                                               "json" -> Json)
  val types: Set[String] = extensions.keySet

  def geoJsonQuery(rs: ResourceScope,
                   id: String,
                   params: Map[String, String]): (String, Response) = {
    val jsonReq = RequestBuilder(GeoJsonHost).
      p("api", "id", s"$id.geojson").
      query(params).
      get

    (URLDecoder.decode(jsonReq.toString, "UTF-8"), http.execute(jsonReq, rs))
  }

  def encode(mapper: CoordinateMapper, response: Response): Option[Array[Byte]] = {
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

  def addToParams(params: Map[String, String],
                  where: String,
                  select: String): Map[String, String] = {
    val whereParam = if (params.contains("$where"))
      params("$where") + s"and $where" else where

    params + ("$where" -> whereParam) + ("$select" -> select)
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

        req.queryParameters map { reqParams: Map[String, String] =>
          val params = addToParams(reqParams, withinBox, pointColumn)
          val (jsonReq, resp) = geoJsonQuery(req.resourceScope, identifier, params)

          resp.resultCode match {
            case 200 => extensions(ext)(mapper, resp)
            case _ => failure("underlying request failed", jsonReq)
          }
        } getOrElse failure("malformed query string")
      }

      override def get = if (types(ext)) {
        req => handleLayer(req, ext)
      } else {
        req => failure("invalid file type")
      }
    }
}
