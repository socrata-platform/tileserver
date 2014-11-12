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
import java.net.URLDecoder.decode
import javax.activation.MimeType
import no.ecc.vectortile.{VectorTileDecoder, VectorTileEncoder}
import org.apache.commons.io.IOUtils
import scala.collection.JavaConverters._

class ImageQueryService(http: HttpClient) extends SimpleResource {
  private val geomFactory = new GeometryFactory

  // TODO: Make this configurable.
  val GeoJsonHost: String = "dataspace.demo.socrata.com"

  val JsonP: ContentP = { o =>
    o match {
      case Some(t) =>
        t.getBaseType.startsWith("application/") && t.getBaseType.endsWith("json")
      case None =>
        false
    }
  }

  sealed trait Extension {
    def formatResponse(mapper: CoordinateMapper, resp: Response): HttpResponse
  }

  object Extension {
    def apply(extension: String) = extensions.apply(extension)
  }

  val invalidJson = InternalServerError ~>
    ContentType("application/json") ~>
    Content("""{"message":"Invalid geo-json returned from underlying service."}""")

  case class Pbf() extends Extension {
    def formatResponse(mapper: CoordinateMapper,
                       resp: Response): HttpResponse = {
      (encode(mapper, resp) map {
         bytes: Array[Byte] => {
           OK ~>
             ContentType("application/octet-stream") ~>
             Header("Access-Control-Allow-Origin", "*") ~>
             ContentBytes(bytes)
         }
       }).getOrElse(invalidJson)
    }
  }

  case class Txt() extends Extension {
    def formatResponse(mapper: CoordinateMapper,
                       resp: Response): HttpResponse = {
      (encode(mapper, resp) map {
         bytes: Array[Byte] => {
           val decoder = new VectorTileDecoder()
           decoder.decode(bytes)
           val features = decoder.getFeatures("main").asScala map {
             _.getGeometry.toString
           }

           OK ~>
             ContentType("text/plain") ~>
             Content(features.mkString("\n"))
         }
       }).getOrElse(invalidJson)
    }
  }

  case class Json() extends Extension {
    def formatResponse(mapper: CoordinateMapper,
                       resp: Response): HttpResponse = {
      OK ~>
        ContentType("application/json") ~>
        Header("Access-Control-Allow-Origin", "*") ~>
        Stream(IOUtils.copy(resp.inputStream(), _))
    }
  }

  val extensions: Map[String, Extension] = Map("pbf" -> Pbf(),
                                               "txt" -> Txt(),
                                               "json" -> Json())
  val types: Set[String] = extensions.keySet

  def geoJsonQuery(rs: ResourceScope,
                   id: String,
                   params: Map[String, String]): (BodylessHttpRequest, Response) = {
    val jsonReq = RequestBuilder(GeoJsonHost).
      p("api", "id", s"$id.geojson").
      query(params).
      get

    (jsonReq, http.execute(jsonReq, rs))
  }

  def encode(mapper: CoordinateMapper, response: Response): Option[Array[Byte]] = {
    val emptyMap = new java.util.HashMap[String, Nothing]()
    val encoder: VectorTileEncoder = new VectorTileEncoder(4096)

    GeoJson.codec.decode(response.jValue(JsonP).toV2) collect {
      case FeatureCollectionJson(features, _) => {
        val coords = features map { _.geometry.getCoordinate }
        val pixels = coords map { mapper.px(_) }
        val points = pixels groupBy { geomFactory.createPoint(_) }
        val counts = points map { case (k, v) => (k, v.size) }

        counts foreach { case (pt, count) =>
          encoder.addFeature("main", emptyMap, pt)
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
      val TypedPathComponent(y, extension) = typedY
      val mapper = CoordinateMapper(z)

      def handleLayer(req: HttpRequest, extension: String): HttpResponse = {
        val quadTile = QuadTile(x, y, z)
        val withinBox = quadTile.withinBox(pointColumn)
        val reqParams = req.queryParameters.getOrElse {
          return BadRequest ~>
            ContentType("application/json") ~>
            Content("""{"message": "malformed query string"}""")
        }

        val params = addToParams(reqParams, withinBox, pointColumn)
        val (jsonReq, resp) = geoJsonQuery(req.resourceScope, identifier, params)

        resp.resultCode match {
          case 200 =>
            Extension(extension).formatResponse(mapper, resp)
          case _ =>
            val jsonReqStr = decode(jsonReq.toString, "UTF-8")
            BadRequest ~> ContentType("application/json") ~>
              Content(s"""{"message":"request failed", "request": "$jsonReqStr"}""")
        }
      }

      override def get = if (types(extension)) {
        req => handleLayer(req, extension)
      } else {
        req => BadRequest
      }
    }
}
