package com.socrata.tileserver

import java.util.concurrent.Executors
import javax.servlet.http.HttpServletRequest

import com.rojoma.simplearm.v2.conversions._
import com.rojoma.simplearm.v2.{Resource, managed}
import com.typesafe.config.ConfigFactory

import com.socrata.backend.client.CoreServerClient
import com.socrata.backend.util.ServiceProviderFromConfig
import com.socrata.http.client.HttpClientHttpClient
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.{HttpRequest, HttpResponse, HttpService, SocrataServerJetty}
import com.socrata.thirdparty.curator.{CuratorFromConfig, CuratorServerProvider, DiscoveryFromConfig}

import config.TileServerConfig
import services.{ImageQueryService, VersionService}

// $COVERAGE-OFF$ Disabled because this is framework boilerplate.
object TileServer extends App {
  private val ListenPort: Int = 2048

  implicit val shutdownTimeout = Resource.executorShutdownNoTimeout
  lazy val config = TileServerConfig(ConfigFactory.load().getConfig("com.socrata"))

  for {
    executor <- managed(Executors.newCachedThreadPool())
    curator <- CuratorFromConfig(config.curator).toV2
    discovery <- DiscoveryFromConfig(classOf[Void],
                                     curator,
                                     config.discovery)
    coreServerCurator <- ServiceProviderFromConfig[Void](discovery, "core")
    http <- managed(new HttpClientHttpClient(executor,
                                             HttpClientHttpClient.
                                               defaultOptions.
                                               withUserAgent("tileserver")))
  } {
    val coreServerProvider = CuratorServerProvider(http, coreServerCurator, identity)
    val core = CoreServerClient(coreServerProvider)

    val imageQueryService = ImageQueryService(core)
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
