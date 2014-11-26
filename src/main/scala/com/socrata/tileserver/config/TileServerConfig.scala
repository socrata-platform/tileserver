package com.socrata.tileserver.config

import com.typesafe.config.Config

import com.socrata.thirdparty.curator.{CuratorConfig, DiscoveryConfig}

class TileServerConfig(config: Config) {
  val port = config.getInt("tileserver.port")
  val curator = new CuratorConfig(config, "curator")
  val discovery = new DiscoveryConfig(config, "curator")
}

object TileServerConfig {
  def apply(config: Config): TileServerConfig = new TileServerConfig(config)
}
