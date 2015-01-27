package com.socrata.tileserver.config

import scala.collection.JavaConverters._

import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.core.ConsoleAppender
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import com.socrata.thirdparty.curator.{CuratedClientConfig, DiscoveryBrokerConfig}

// $COVERAGE-OFF$ Disabled because this is configuration boilerplate.
/** Container for global configuration. */
object TileServerConfig {
  private lazy val config = ConfigFactory.load().getConfig("com.socrata")

  /** Port to listen on. */
  lazy val port = config.getInt("tileserver.port")

  /** Zookeeper configuration. */
  lazy val broker = new DiscoveryBrokerConfig(config, "curator")

  /** Geo-Json Service Config. */
  lazy val upstream = new CuratedClientConfig(config, "upstream")

  val logLevel = Level.toLevel(config.getString("logback.loglevel"), Level.INFO)

  LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) match {
    case root: Logger => root.setLevel(logLevel)
  }
}
// $COVERAGE-ON$
