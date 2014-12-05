package com.socrata.backend
package client

import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit.SECONDS
import scala.util.{Failure, Success, Try}

import com.rojoma.json.v3.ast.{JString, JValue}
import org.slf4j.LoggerFactory

import com.socrata.http.client.{RequestBuilder, Response, SimpleHttpRequest}
import com.socrata.http.server.HttpResponse
import com.socrata.thirdparty.curator.ServerProvider
import com.socrata.thirdparty.curator.ServerProvider.{Complete, Retry}

import errors.{ServiceDiscoveryException}

// TODO: Pull this into its own project.
// TODO: Consider defining a trait for this in SocrataHttp.
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
  def execute[T](request: RequestBuilder => SimpleHttpRequest,
                 callback: Response => T): T = {
    coreProvider.withRetries(maxRetries,
                             request,
                             ServerProvider.RetryOnAllExceptionsDuringInitialRequest) {
      case Some(response) => Complete(callback(response))
      case None => throw ServiceDiscoveryException("Failed to discover core server.")
    }
  }
}

object CoreServerClient {
  private val DefaultConnectTimeoutSec: Int = 10
  private val DefaultMaxRetries: Int = 5

  def apply(coreProvider: ServerProvider,
            connectTimeout: FiniteDuration = new FiniteDuration(DefaultConnectTimeoutSec, SECONDS),
            maxRetries: Int = DefaultMaxRetries): CoreServerClient = {
    if (connectTimeout.toMillis != connectTimeout.toMillis.toInt) {
      throw new IllegalArgumentException(
        "Connect timeout out of range (milliseconds must fit in an Int).")
    }

    new CoreServerClient(coreProvider, connectTimeout.toMillis.toInt, maxRetries)
  }
}
