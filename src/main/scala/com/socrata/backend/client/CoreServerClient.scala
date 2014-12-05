package com.socrata.backend
package client

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

import com.rojoma.json.v3.ast.{JString, JValue}
import org.slf4j.LoggerFactory

import com.socrata.http.client.{RequestBuilder, Response, SimpleHttpRequest}
import com.socrata.http.server.HttpResponse
import com.socrata.thirdparty.curator.ServerProvider
import com.socrata.thirdparty.curator.ServerProvider.{Complete, Retry}

import errors.ServiceDiscoveryException
import config.CoreServerClientConfig

// TODO: Pull this into its own project.
/**
  * Manages connections and requests to the Soda Fountain service
  * @param coreProvider Service discovery object
  * @param connectTimeout Timeout setting for connecting to the service
  */
case class CoreServerClient(coreProvider: ServerProvider,
                            config: CoreServerClientConfig) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val connectTimeoutMs = config.ConnectTimeout.toMillis
  private val maxRetries = config.MaxRetries

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
