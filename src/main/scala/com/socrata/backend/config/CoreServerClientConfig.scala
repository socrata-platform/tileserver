package com.socrata.backend
package config

import java.util.concurrent.TimeUnit.SECONDS
import scala.concurrent.duration.FiniteDuration

trait CoreServerClientConfig {
  def maxRetries: Int
  def connectTimeoutSec: Int

  final def connectTimeout: FiniteDuration = {
    val connectTimeout = new FiniteDuration(connectTimeoutSec, SECONDS)

    if (connectTimeout.toMillis != connectTimeout.toMillis.toInt) {
      throw new IllegalArgumentException(
        "Connect timeout out of range (milliseconds must fit in an Int).")
    }

    connectTimeout
  }
}
