package com.socrata.tileserver.config

import com.typesafe.config.ConfigFactory

import com.socrata.thirdparty.curator.{CuratedClientConfig, CuratorConfig, DiscoveryConfig}

// $COVERAGE-OFF$ Disabled because this is configuration boilerplate.
/** Container for global configuration. */
object TileServerConfig {
  private lazy val config = ConfigFactory.load().getConfig("com.socrata")

  /** Port to listen on. */
  lazy val port = config.getInt("tileserver.port")

  /** Zookeeper configuration. */
  lazy val curator = new CuratorConfig(config, "curator")

  /** Zookeeper configuration. */
  lazy val discovery = new DiscoveryConfig(config, "curator")

  /** Geo-Json Service Config. */
  lazy val upstream = new CuratedClientConfig(config, "upstream")
}
// $COVERAGE-ON$
