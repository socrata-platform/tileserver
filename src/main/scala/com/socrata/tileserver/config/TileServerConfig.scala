package com.socrata.tileserver.config

import com.typesafe.config.ConfigFactory

import com.socrata.thirdparty.curator.{CuratorConfig, DiscoveryConfig}
import com.socrata.backend.config.CoreServerClientConfig

// $COVERAGE-OFF$ Disabled because this is configuration boilerplate.
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
  lazy val tileSize: Int = config.getInt("tileserver.tile-size")

  /** Max client retries. */
  lazy val maxRetries = config.getInt("backend.max-retries")

  /** Client timeout. */
  lazy val connectTimeoutSec = config.getInt("backend.connect-timeout")
}
// $COVERAGE-ON$
