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

  val emptyMap = new java.util.HashMap[String, Nothing]()
  val types: Set[String] = Set("pbf", "txt", "json")

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
    val encoder: VectorTileEncoder = new VectorTileEncoder(4096)

    GeoJson.codec.decode(response.jValue(JsonP).toV2) collect {
      case FeatureCollectionJson(features, _) => {
        val coords = features groupBy { _.geometry.getCoordinate } map {
          case (k, v) => (k, v.size)
        }

        coords foreach { case (coord, count) =>
          val pt = geomFactory.createPoint(mapper.px(coord))
          encoder.addFeature("main", emptyMap, pt)
        }

        encoder.encode()
      }
    }
  }

  def createPbf(mapper: CoordinateMapper, resp: Response): Option[HttpResponse] = {
    encode(mapper, resp) map {
      bytes => {
        OK ~>
          ContentType("application/octet-stream") ~>
          Header("Access-Control-Allow-Origin", "*") ~>
          ContentBytes(bytes)
      }
    }
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
          return BadRequest // TODO: malformed query string
        }

        val whereParams = if (reqParams.contains("$where"))
          reqParams + ("$where" -> { reqParams("$where") + s"and $withinBox" })
        else
          reqParams + ("$where" -> withinBox)

        val params = whereParams + ("$select" -> s"$pointColumn")

        val (jsonReq, resp) = geoJsonQuery(req.resourceScope, identifier, params)

        resp.resultCode match {
          case 200 =>
            val payload =
              extension match {
                case "pbf" =>
                  createPbf(mapper, resp)
                case "txt" =>
                  encode(mapper, resp) map {
                    bytes => {
                      import scala.collection.JavaConverters._

                      val decoder = new VectorTileDecoder()
                      decoder.decode(bytes)
                      val features = decoder.getFeatures("main").asScala map {
                        _.getGeometry.toString
                      }

                      OK ~>
                        ContentType("text/plain") ~>
                        Content(features.mkString("\n"))
                    }
                  }
                case "json" =>
                  Some(OK ~>
                         ContentType("application/json") ~>
                         Header("Access-Control-Allow-Origin", "*") ~>
                         Stream(IOUtils.copy(resp.inputStream(), _)))
                case _ =>
                  None // If properly configured, we will never get here.
              }
            payload match {
              case Some(response) =>
                response
              case None =>
                InternalServerError ~>
                  ContentType("application/json") ~>
                  Content("""{"message":"Invalid geo-json returned from underlying service."}""")
            }
          case _ =>
            val jsonReqStr = decode(jsonReq.toString, "UTF-8")
            BadRequest ~> ContentType("application/json") ~>
              Content(s"""{"message":"request failed", "identifier":"$identifier",
"params":"$params","request": "$jsonReqStr"}""")
        }
      }

      override def get = if (types(extension)) {
        req => handleLayer(req, extension)
      } else {
        req => BadRequest
      }
    }
}
