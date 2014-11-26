package com.socrata.tileserver

import java.util.concurrent.Executors
import javax.servlet.http.HttpServletRequest

import com.rojoma.json.v3.interpolation._
import com.rojoma.simplearm.v2.{Resource, managed}
import org.slf4j.LoggerFactory

import com.socrata.http.client.HttpClientHttpClient
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.routing.SimpleRouteContext.{Route, Routes}
import com.socrata.http.server.routing.TypedPathComponent
import com.socrata.http.server.{HttpRequest, HttpResponse, HttpService, SocrataServerJetty}

import services.{ImageQueryService, VersionService}

// $COVERAGE-OFF$ Disabled because this is basically configuration.
class Router(versionService: HttpService,
             imageQueryTypes : String => Boolean,
             imageQueryService: (String,
                                 String,
                                 Int,
                                 Int,
                                 TypedPathComponent[Int]) => HttpService) {
  private val logger = LoggerFactory.getLogger(getClass)

  val routes = Routes(
    Route("/version", versionService),
    // domain/tiles/abcd-1234/pointColumn/z/x/y.pbf
    Route("/tiles/{String}/{String}/{Int}/{Int}/{{Int!imageQueryTypes}}",
          imageQueryService)
  )

  val notFound: HttpService = req => {
    logger.warn(s"path not found: ${req.requestPathStr}")
    NotFound ~>
      Json(json"""{error:"not found"}""")
  }

  def route(req: HttpRequest): HttpResponse =
    routes(req.requestPath).getOrElse(notFound)(req)
}

object TileServer extends App {
  private val ListenPort: Int = 2048
  implicit val shutdownTimeout = Resource.executorShutdownNoTimeout

  for {
    executor <- managed(Executors.newCachedThreadPool())
    http <- managed(new HttpClientHttpClient(executor,
                                             HttpClientHttpClient.
                                               defaultOptions.
                                               withUserAgent("tileserver")))
  } {
    val imageQueryService = ImageQueryService(http)
    val router = new Router(VersionService,
                            imageQueryService.types,
                            imageQueryService.service)
    val handler = router.route _

    val server = new SocrataServerJetty(
      handler,
      SocrataServerJetty.defaultOptions.withPort(ListenPort))

    server.run()
  }
}
// $COVERAGE-ON$
