package com.socrata.backend
package client

import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit.SECONDS
import scala.util.{Failure, Success, Try}

import com.rojoma.json.v3.ast.{JString, JValue}
import org.slf4j.LoggerFactory

import com.socrata.http.client.{RequestBuilder, Response, SimpleHttpRequest}
import com.socrata.thirdparty.curator.ServerProvider
import com.socrata.thirdparty.curator.ServerProvider.{Complete, Retry}

import errors.{ServiceDiscoveryException}

/**
  * Manages connections and requests to the Soda Fountain service
  * @param coreProvider Service discovery object
  * @param connectTimeout Timeout setting for connecting to the service
  */
class CoreServerClient(coreProvider: ServerProvider,
                       connectTimeoutMs: Int,
                       maxRetries: Int) {
  private val logger = LoggerFactory.getLogger(getClass)

  /**
    * Sends a get request to Soda Fountain
    * and returns the response
    * @return HTTP response code and body
    */
  def execute(request: RequestBuilder => SimpleHttpRequest): Response = {
    coreProvider.withRetries(maxRetries,
                             request,
                             ServerProvider.RetryOnAllExceptionsDuringInitialRequest) {
      case Some(response) => Complete(response)
      case None => throw ServiceDiscoveryException("core server")
    }
  }
}

object CoreServerClient {
  def apply(coreProvider: ServerProvider,
            connectTimeout: FiniteDuration = new FiniteDuration(10, SECONDS),
            maxRetries: Int = 5): CoreServerClient = {
    if (connectTimeout.toMillis != connectTimeout.toMillis.toInt) {
      throw new IllegalArgumentException(
        "Connect timeout out of range (milliseconds must fit in an Int).")
    }

    new CoreServerClient(coreProvider, connectTimeout.toMillis.toInt, maxRetries)
  }
}
