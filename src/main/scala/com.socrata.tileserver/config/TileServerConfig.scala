package com.socrata.tileserver.config

import com.typesafe.config.ConfigFactory

import com.socrata.curator.{CuratedClientConfig, DiscoveryBrokerConfig}

import java.util.concurrent.TimeUnit.SECONDS

// $COVERAGE-OFF$ Disabled because this is configuration boilerplate.
/** Container for global configuration. */
object TileServerConfig {
  private lazy val config = ConfigFactory.load().getConfig("com.socrata")

  /** Port to listen on. */
  lazy val port = config.getInt("tileserver.port")

  /** Thread pool settings. */
  lazy val threadpool = config.getConfig("tileserver.threadpool")

  /** CartoCSS Renderer Base Url */
  lazy val renderHost = config.getString("tileserver.render-host")
  lazy val renderPort = config.getInt("tileserver.render-port")

  /** Query configuration. */
  lazy val queryTimeout = config.getDuration("tileserver.query-timeout", SECONDS).toString

  /** Zookeeper configuration. */
  lazy val broker = new DiscoveryBrokerConfig(config, "curator")

  /** Geo-Json Service Config. */
  lazy val upstream = new CuratedClientConfig(config, "upstream")
}
// $COVERAGE-ON$
