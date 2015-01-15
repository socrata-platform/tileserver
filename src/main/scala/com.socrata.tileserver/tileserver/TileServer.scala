package com.socrata.tileserver

import java.util.concurrent.Executors
import javax.servlet.http.HttpServletRequest

import com.rojoma.simplearm.v2.conversions._
import com.rojoma.simplearm.v2.{Resource, managed}

import com.socrata.http.client.HttpClientHttpClient
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.{HttpRequest, HttpResponse, HttpService, SocrataServerJetty}
import com.socrata.thirdparty.curator._

import config.TileServerConfig
import services.{TileService, VersionService}

// $COVERAGE-OFF$ Disabled because this is framework boilerplate.
/** Main object that actually starts up the server; wires everything together. */
object TileServer extends App {
  /** Never timeout shutting down an executor. */
  implicit val shutdownTimeout = Resource.executorShutdownNoTimeout

  for {
    executor <- managed(Executors.newCachedThreadPool())
    curator <- CuratorFromConfig(TileServerConfig.curator).toV2
    discovery <- DiscoveryFromConfig(classOf[Void],
                                     curator,
                                     TileServerConfig.discovery)
    http <- managed(new HttpClientHttpClient(executor,
                                             HttpClientHttpClient.
                                               defaultOptions.
                                               withUserAgent("tileserver")))
    coreServerCurator <- ServiceProviderFromName[Void](discovery, TileServerConfig.upstream.serviceName)
  } {
    val upstreamServerProvider = CuratorServerProvider(http, coreServerCurator, identity)
    val upstream = CuratedServiceClient(upstreamServerProvider, TileServerConfig.upstream)

    val tileService = TileService(upstream)
    val router = new Router(VersionService, tileService.types, tileService.service)

    val server = new SocrataServerJetty(
      handler = router.route,
      options = SocrataServerJetty.defaultOptions.withPort(TileServerConfig.port))

    server.run()
  }
}
  // $COVERAGE-ON$
