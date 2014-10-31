import com.socrata.http.server.SocrataServerJetty
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses.{BadRequest, Content}
import com.socrata.http.server.routing.TypedPathComponent
import com.socrata.http.server.routing.SimpleRouteContext.{Route, Routes}
import com.socrata.http.server.{HttpResponse, HttpService}
import javax.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory

class Router(healthService: => HttpService,
             imageQueryTypes : String => Boolean,
             imageQueryService: (String,
                                 String,
                                 String,
                                 Int,
                                 Int,
                                 TypedPathComponent[Int]) => HttpService) {
  val logger = LoggerFactory.getLogger(getClass)

  val routes = Routes(
    Route("/health", healthService),
    Route("/tiles/{String}/{String}/{String}/{Int}/{Int}/{{Int!imageQueryTypes}}",
          imageQueryService)
  )

  def route(req: HttpServletRequest): HttpResponse =
    routes(req.requestPath) match {
      case Some(s) =>
        s(req)
      case None =>
        BadRequest ~> Content("""{"error": "bad request" }""")
    }
}

object TileServer extends App {
  val router = new Router(HealthService,
                          ImageQueryService.types,
                          ImageQueryService.service)
  val handler = router.route _

  val server = new SocrataServerJetty(
    handler,
    SocrataServerJetty.defaultOptions.withPort(2048))

  server.run()
}
