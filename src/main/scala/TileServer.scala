import com.socrata.http.server.SocrataServerJetty
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.routing.TypedPathComponent
import com.socrata.http.server.routing.SimpleRouteContext.{Route, Routes}
import com.socrata.http.server.{HttpRequest, HttpResponse, HttpService}
import javax.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory

class Router(healthService: HttpService,
             imageQueryTypes : String => Boolean,
             imageQueryService: (String,
                                 String,
                                 Int,
                                 Int,
                                 TypedPathComponent[Int]) => HttpService) {
  val logger = LoggerFactory.getLogger(getClass)

  val routes = Routes(
    Route("/health", healthService),
    // domain/tiles/abcd-1234/pointColumn/z/x/y.pbf
    Route("/tiles/{String}/{String}/{Int}/{Int}/{{Int!imageQueryTypes}}",
          imageQueryService)
  )

  def route(req: HttpRequest): HttpResponse =
    req.requestPath match {
      case Some(path) =>
        routes(path) match {
          case Some(s) =>
            s(req)
          case None =>
            NotFound ~> ContentType("application/json") ~> Content("""{"error": "not found" }""")
        }
      case None =>
        BadRequest ~> ContentType("application/json") ~> Content("""{"error": "bad request" }""")
    }
}

object TileServer extends App {
  import java.util.concurrent.Executors
  import com.socrata.http.client.HttpClientHttpClient
  import com.rojoma.simplearm.v2._
  implicit def shutdownTimeout = Resource.executorShutdownNoTimeout
  for {
    executor <- managed(Executors.newCachedThreadPool())
    http <- managed(new HttpClientHttpClient(executor, HttpClientHttpClient.defaultOptions.
                                               withUserAgent("tile server")))
  } {
    val imageQueryService = new ImageQueryService(http)

    val router = new Router(HealthService,
                            imageQueryService.types,
                            imageQueryService.service)
    val handler = router.route _

    val server = new SocrataServerJetty(
      handler,
      SocrataServerJetty.defaultOptions.withPort(2048))

    server.run()
  }
}
