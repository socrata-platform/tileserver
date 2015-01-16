package com.socrata.thirdparty.curator

import com.typesafe.config.Config

class DiscoveryBrokerConfig(config: Config, root: String) {
  /** Zookeeper configuration. */
  lazy val curator = new CuratorConfig(config, "curator")

  /** Zookeeper configuration. */
  lazy val discovery = new DiscoveryConfig(config, "curator")
}
