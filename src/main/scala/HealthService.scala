import com.socrata.http.server.implicits.httpResponseToChainedResponse
import com.socrata.http.server.responses.{OK, ContentType, Content}
import com.socrata.http.server.routing.SimpleResource

class HealthService extends SimpleResource {
  override def get = { req =>
    OK ~>
      ContentType("application/json") ~>
      Content("""{"health":"alive"}""")
  }
}
