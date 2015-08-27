package com.socrata.tileserver

import java.util.concurrent.Executors
import javax.servlet.http.HttpServletRequest

import com.rojoma.simplearm.v2.conversions._
import com.rojoma.simplearm.v2.{Resource, managed}

import com.socrata.http.client.{HttpClientHttpClient, RequestBuilder}
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.{HttpRequest, HttpResponse, HttpService, SocrataServerJetty}
import com.socrata.curator._

import config.TileServerConfig
import services.{TileService, VersionService}
import util.{CartoRenderer, GeoProvider}

// $COVERAGE-OFF$ Disabled because this is framework boilerplate.
/** Main object that actually starts up the server; wires everything together. */
object TileServer extends App {
  /** Never timeout shutting down an executor. */
  implicit val shutdownTimeout = Resource.executorShutdownNoTimeout

  for {
    executor <- managed(Executors.newCachedThreadPool())
    http <- managed(new HttpClientHttpClient(executor,
                                             HttpClientHttpClient.
                                               defaultOptions.
                                               withUserAgent("tileserver")))
    broker <- DiscoveryBrokerFromConfig(TileServerConfig.broker, http)
    upstream <- broker.clientFor(TileServerConfig.upstream)
  } {
    val cartoBaseUrl = RequestBuilder(TileServerConfig.cartoHost).
      port(TileServerConfig.cartoPort)

    val renderer = CartoRenderer(http, cartoBaseUrl)
    val provider = GeoProvider(upstream)
    val tileService = TileService(renderer, provider)
    val router = Router(VersionService, tileService.types, tileService.service)

    val server = new SocrataServerJetty(
      handler = router.route,
      options = SocrataServerJetty.defaultOptions.
        withPort(TileServerConfig.port).
        withPoolOptions(SocrataServerJetty.Pool(TileServerConfig.threadpool)))

    server.run()
  }
}
// $COVERAGE-ON$
