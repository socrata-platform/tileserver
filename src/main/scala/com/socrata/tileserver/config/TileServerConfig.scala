package com.socrata.tileserver.config

import com.typesafe.config.{Config, ConfigFactory}

import com.socrata.thirdparty.curator.{CuratorConfig, DiscoveryConfig}

object TileServerConfig {
  private val config = ConfigFactory.load().getConfig("com.socrata")

  val port = config.getInt("tileserver.port")
  val curator = new CuratorConfig(config, "curator")
  val discovery = new DiscoveryConfig(config, "curator")
}
