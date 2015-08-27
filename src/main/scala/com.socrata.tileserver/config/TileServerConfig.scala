package com.socrata.tileserver.config

import com.typesafe.config.ConfigFactory

import com.socrata.curator.{CuratedClientConfig, DiscoveryBrokerConfig}

// $COVERAGE-OFF$ Disabled because this is configuration boilerplate.
/** Container for global configuration. */
object TileServerConfig {
  private lazy val config = ConfigFactory.load().getConfig("com.socrata")

  /** Port to listen on. */
  lazy val port = config.getInt("tileserver.port")

  /** Thread pool settings. */
  lazy val threadpool = config.getConfig("tileserver.threadpool")

  /** CartoCSS Renderer Base Url */
  lazy val cartoHost = config.getString("tileserver.carto-host")
  lazy val cartoPort = config.getInt("tileserver.carto-port")

  /** Zookeeper configuration. */
  lazy val broker = new DiscoveryBrokerConfig(config, "curator")

  /** Geo-Json Service Config. */
  lazy val upstream = new CuratedClientConfig(config, "upstream")
}
// $COVERAGE-ON$
