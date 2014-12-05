package com.socrata.backend
package config

import java.util.concurrent.TimeUnit.SECONDS
import scala.concurrent.duration.FiniteDuration

trait CoreServerClientConfig {
  def MaxRetries: Int
  def ConnectTimeoutSec: Int

  final def ConnectTimeout: FiniteDuration = {
    val connectTimeout = new FiniteDuration(ConnectTimeoutSec, SECONDS)

    if (connectTimeout.toMillis != connectTimeout.toMillis.toInt) {
      throw new IllegalArgumentException(
        "Connect timeout out of range (milliseconds must fit in an Int).")
    }

    connectTimeout
  }
}
