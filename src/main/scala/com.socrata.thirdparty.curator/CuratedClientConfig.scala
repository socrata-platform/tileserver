package com.socrata.thirdparty.curator

import java.util.concurrent.TimeUnit.SECONDS
import scala.concurrent.duration.FiniteDuration

import com.typesafe.config.Config

import com.socrata.thirdparty.typesafeconfig.ConfigClass

class CuratedClientConfig(config: Config,
                          root: String) extends ConfigClass(config, root) {
  val serviceName = getString("service-name")
  val maxRetries = getInt("max-retries")
  val connectTimeoutSec = getInt("connect-timeout")

  val connectTimeout: FiniteDuration = {
    val connectTimeout = new FiniteDuration(connectTimeoutSec, SECONDS)

    if (connectTimeout.toMillis != connectTimeout.toMillis.toInt) {
      throw new IllegalArgumentException(
        "Connect timeout out of range (milliseconds must fit in an Int).")
    }

    connectTimeout
  }
}
