import com.socrata.http.server.implicits.httpResponseToChainedResponse
import com.socrata.http.server.responses.{OK, InternalServerError, ContentType, Content}
import com.socrata.http.server.routing.{SimpleResource, TypedPathComponent}

case class QuadTile(rawX: Int, rawY: Int, z: Int) {
  val depth: Int = Math.pow(2, z).toInt
  val x: Int = rawX % depth
  val y: Int = rawY % depth

  val interval: Seq[Double] = {
    Seq.empty
  }

  def extent(interval: Seq[Double]): Seq[(Double, Double)] = {
    Seq((interval(0), interval(1)), (interval(2), interval(1)),
        (interval(2), interval(3)), (interval(0), interval(3)),
        (interval(0), interval(1)))
  }

  def sql(schemaName: String, tableName: String) {
    val e: Seq[(Double, Double)] = extent(interval)
    val fmtE = e.map { case (a, b) => f"$a%f $b%f" } mkString(", ")
    val wktString = f"POLYGON (($fmtE%s))"

    f"""
     SELECT *
       FROM $schemaName%s.$tableName%s
      WHERE "geometry".ST_Intersects(ST_Geography('$wktString%s'));
     """
  }
}

object ImageQueryService extends SimpleResource {
  val types: Set[String] = Set("json")

  def service(instanceName: String,
              schemaName: String,
              tableName: String,
              z: Int,
              x: Int,
              typedY: TypedPathComponent[Int]) =
    new SimpleResource {
      val TypedPathComponent(y, extension) = typedY

      def handleLayer = {
        val quadTile = QuadTile(x, y, z)
        val queryString = quadTile.sql(schemaName, tableName)

        // val stream = scQuery(ServerName, 8080, instanceName, queryString, gjCollect)

        OK ~>
          ContentType("application/json") ~>
          Content("")
      }

      override def get = extension match {
        case "json" =>
          req => handleLayer
        case _ =>
          req => InternalServerError
      }
    }
}
