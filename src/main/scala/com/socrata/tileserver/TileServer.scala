package com.socrata.tileserver

import java.util.concurrent.Executors
import javax.servlet.http.HttpServletRequest

import com.rojoma.simplearm.v2.conversions._
import com.rojoma.simplearm.v2.{Resource, managed}

import com.socrata.backend.client.CoreServerClient
import com.socrata.backend.util.ServiceProviderFromConfig
import com.socrata.http.client.HttpClientHttpClient
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.{HttpRequest, HttpResponse, HttpService, SocrataServerJetty}
import com.socrata.thirdparty.curator.{CuratorFromConfig, CuratorServerProvider, DiscoveryFromConfig}

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
    coreServerCurator <- ServiceProviderFromConfig[Void](discovery, "core")
    http <- managed(new HttpClientHttpClient(executor,
                                             HttpClientHttpClient.
                                               defaultOptions.
                                               withUserAgent("tileserver")))
  } {
    val coreServerProvider = CuratorServerProvider(http, coreServerCurator, identity)
    val core = CoreServerClient(coreServerProvider, TileServerConfig)

    val imageQueryService = TileService(core)
    val router = new Router(VersionService,
                            imageQueryService.types,
                            imageQueryService.service)
    val handler = router.route _

    val server = new SocrataServerJetty(
      handler,
      SocrataServerJetty.defaultOptions.withPort(TileServerConfig.port))

    server.run()
  }
}
// $COVERAGE-ON$
