import com.rojoma.json.v3.ast.JValue
import com.rojoma.simplearm.v2.{Managed, ResourceScope}
import com.socrata.http.client.{HttpClient, RequestBuilder, Response, BodylessHttpRequest}
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.routing.{SimpleResource, TypedPathComponent}
import com.socrata.http.server.{HttpRequest, HttpResponse}
import java.net.URLDecoder.decode
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import scala.util.{Try, Success, Failure}

class ImageQueryService(http: HttpClient) extends SimpleResource {
  // TODO: Make this configurable.
  val GeoJsonHost: String = "dataspace.demo.socrata.com"

  val types: Set[String] = Set("json")

  def geoJsonQuery(rs: ResourceScope, id: String, whereParams: Map[String, String]): (BodylessHttpRequest, Response) = {
    val jsonReq = RequestBuilder(GeoJsonHost).
      p("api", "id", s"$id.geojson").
      query(whereParams).
      get

    (jsonReq, http.execute(jsonReq, rs))
  }

  def service(identifier: String,
              pointColumn: String,
              z: Int,
              x: Int,
              typedY: TypedPathComponent[Int]) =
    new SimpleResource {
      val TypedPathComponent(y, extension) = typedY

      def handleLayer(req: HttpRequest): HttpResponse = {
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
            val ct = resp.headers("content-type").headOption
            val ctHeader = ct.fold(NoOp)(ContentType)
            OK ~>
              ctHeader ~>
              Header("Access-Control-Allow-Origin", "*") ~>
              Stream(IOUtils.copy(resp.inputStream(), _))
          case _ =>
            val jsonReqStr = decode(jsonReq.toString, "UTF-8")
            BadRequest ~> ContentType("application/json") ~>
              Content(s"""{"message":"request failed", "identifier":"$identifier",
"params":"$params","request": "$jsonReqStr"}""")
        }
      }

      override def get = extension match {
        case "json" =>
          req => handleLayer(req)
      }
    }
}
