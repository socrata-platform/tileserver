package com.socrata.tileserver.config

import com.typesafe.config.ConfigFactory

import com.socrata.thirdparty.curator.{CuratorConfig, DiscoveryConfig}
import com.socrata.backend.config.CoreServerClientConfig

/** Container for global configuration. */
object TileServerConfig extends CoreServerClientConfig {
  private lazy val config = ConfigFactory.load().getConfig("com.socrata")

  /** Port to listen on. */
  lazy val Port = config.getInt("tileserver.port")

  /** Zookeeper configuration. */
  lazy val Curator = new CuratorConfig(config, "curator")

  /** Zookeeper configuration. */
  lazy val Discovery = new DiscoveryConfig(config, "curator")

  /** The size (in points) of the tiles. */
  lazy val TileSize: Int = config.getInt("tileserver.tileSize")

  /** Extent for vector tile encoder. */
  lazy val TileExtent: Int = config.getInt("tileserver.tileExtent")

  /** Max client retries. */
  lazy val MaxRetries = config.getInt("backend.maxRetries")

  /** Client timeout. */
  lazy val ConnectTimeoutSec = config.getInt("backend.connectTimeout")
}
