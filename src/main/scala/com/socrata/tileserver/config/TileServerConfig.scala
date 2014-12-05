package com.socrata.tileserver.config

import com.typesafe.config.{Config, ConfigFactory}

import com.socrata.thirdparty.curator.{CuratorConfig, DiscoveryConfig}

/** Container for global configuration. */
object TileServerConfig {
  private lazy val config = ConfigFactory.load().getConfig("com.socrata")

  /** Port to listen on. */
  lazy val port = config.getInt("tileserver.port")

  /** Zookeeper configuration. */
  lazy val curator = new CuratorConfig(config, "curator")

  /** Zookeeper configuration. */
  lazy val discovery = new DiscoveryConfig(config, "curator")
}
