package com.socrata.tileserver

import java.util.concurrent.Executors
import javax.servlet.http.HttpServletRequest

import com.rojoma.simplearm.v2.conversions._
import com.rojoma.simplearm.v2.{Resource, managed}

import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.{HttpRequest, HttpResponse, HttpService, SocrataServerJetty}
import com.socrata.thirdparty.curator._

import config.TileServerConfig
import services.{TileService, VersionService}

// $COVERAGE-OFF$ Disabled because this is framework boilerplate.
/** Main object that actually starts up the server; wires everything together. */
object TileServer extends App {
  for {
    broker <- DiscoveryBrokerFromConfig(TileServerConfig.broker, "tileserver")
    upstream <- broker.clientFor(TileServerConfig.upstream)
  } {
    val tileService = TileService(upstream)
    val router = new Router(VersionService, tileService.types, tileService.service)

    val server = new SocrataServerJetty(
      handler = router.route,
      options = SocrataServerJetty.defaultOptions.withPort(TileServerConfig.port))

    server.run()
  }
}
// $COVERAGE-ON$
