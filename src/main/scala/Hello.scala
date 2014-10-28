import com.socrata.http.server.SocrataServerJetty
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses.{BadRequest, Content}
import com.socrata.http.server.routing.SimpleRouteContext.{Route, Routes}
import com.socrata.http.server.{HttpResponse, HttpService}
import javax.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory

class Router(helloService: => HttpService) {
  val logger = LoggerFactory.getLogger(getClass)

  val routes = Routes(
    Route("/hello", helloService))

  def route(req: HttpServletRequest): HttpResponse =
    routes(req.requestPath) match {
      case Some(s) =>
        s(req)
      case None =>
        BadRequest ~> Content("""{"error": "bad request" }""")
    }
}

object Hello extends App {
  val router = new Router(new HelloService())
  val handler = router.route _

  val server = new SocrataServerJetty(
    handler = handler,
    port = 8080)

  server.run()
}
