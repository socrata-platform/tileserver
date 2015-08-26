package com.socrata.tileserver

import com.rojoma.json.v3.interpolation._
import org.slf4j.LoggerFactory

import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.routing.SimpleRouteContext.{Route, Routes}
import com.socrata.http.server.routing.TypedPathComponent
import com.socrata.http.server.util.RequestId.ReqIdHeader
import com.socrata.http.server.util.handlers.{LoggingOptions, NewLoggingHandler}
import com.socrata.http.server.{HttpRequest, HttpResponse, HttpService}

// $COVERAGE-OFF$ Disabled because this is basically configuration.
case class Router(versionService: HttpService,
                  tileTypes : String => Boolean,
                  tileService: (String,
                                String,
                                Int,
                                Int,
                                TypedPathComponent[Int]) => HttpService) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val logWrapper =
    NewLoggingHandler(LoggingOptions(logger, Set("X-Socrata-Host",
                                                 "X-Socrata-Resource",
                                                 ReqIdHeader))) _

  /** Routing table. */
  val routes = Routes(
    Route("/version", versionService),
    // domain/tiles/abcd-1234/pointColumn/z/x/y.pbf
    Route("/tiles/{String}/{String}/{Int}/{Int}/{{Int!tileTypes}}", tileService)
  )

  /** 404 error. */
  val notFound: HttpService = req => {
    logger.warn("path not found: {}", req.requestPathStr)
    NotFound ~>
      Json(json"""{error:"not found"}""")
  }

  val route: HttpRequest => HttpResponse = req =>
  logWrapper(routes(req.requestPath).getOrElse(notFound))(req)
}
// $COVERAGE-ON$
