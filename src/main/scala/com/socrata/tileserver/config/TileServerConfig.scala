package com.socrata.tileserver.config

import com.typesafe.config.ConfigFactory

import com.socrata.thirdparty.curator.{CuratorConfig, DiscoveryConfig}
import com.socrata.backend.config.CoreServerClientConfig

/** Container for global configuration. */
object TileServerConfig extends CoreServerClientConfig {
  private lazy val config = ConfigFactory.load().getConfig("com.socrata")

  /** Port to listen on. */
  lazy val port = config.getInt("tileserver.port")

  /** Zookeeper configuration. */
  lazy val curator = new CuratorConfig(config, "curator")

  /** Zookeeper configuration. */
  lazy val discovery = new DiscoveryConfig(config, "curator")

  /** The size (in points) of the tiles. */
  lazy val tileSize: Int = config.getInt("tileserver.tileSize")

  /** Extent for vector tile encoder. */
  lazy val tileExtent: Int = config.getInt("tileserver.tileExtent")

  /** Max client retries. */
  lazy val maxRetries = config.getInt("backend.maxRetries")

  /** Client timeout. */
  lazy val connectTimeoutSec = config.getInt("backend.connectTimeout")
}
