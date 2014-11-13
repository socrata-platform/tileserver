package com.socrata.tileserver

import com.rojoma.simplearm.v2.{Resource, managed}
import com.socrata.http.client.HttpClientHttpClient
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.routing.SimpleRouteContext.{Route, Routes}
import com.socrata.http.server.routing.TypedPathComponent
import com.socrata.http.server.{HttpRequest, HttpResponse, HttpService, SocrataServerJetty}
import java.util.concurrent.Executors
import javax.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import services.{HealthService, ImageQueryService}

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

  val notFound: HttpService = req => {
    logger.warn(s"path not found: ${req.requestPathStr}")
    NotFound ~>
      ContentType("application/json") ~>
      Content("""{"error": "not found" }""")
  }

  val badRequest: HttpService = req => {
    logger.warn(s"bad request")
    BadRequest ~>
      ContentType("application/json") ~>
      Content("""{"error": "bad request" }""")
  }

  def route(req: HttpRequest): HttpResponse = req.requestPath match {
    case Some(path) => routes(path).getOrElse(notFound)(req)
    case None => badRequest(req)
  }
}

object TileServer extends App {
  implicit val shutdownTimeout = Resource.executorShutdownNoTimeout

  for {
    executor <- managed(Executors.newCachedThreadPool())
    http <- managed(new HttpClientHttpClient(executor,
                                             HttpClientHttpClient.
                                               defaultOptions.
                                               withUserAgent("tileserver")))
  } {
    val imageQueryService = ImageQueryService(http)
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
