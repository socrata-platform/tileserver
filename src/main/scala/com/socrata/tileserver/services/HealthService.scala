package com.socrata.tileserver.services

import org.slf4j.LoggerFactory

import com.socrata.http.server.implicits.httpResponseToChainedResponse
import com.socrata.http.server.HttpService
import com.socrata.http.server.responses.{OK, ContentType, Content}
import com.socrata.http.server.routing.SimpleResource

object HealthService extends SimpleResource {
  val logger = LoggerFactory.getLogger(getClass)

  override def get: HttpService = { req =>
    logger.info("Alive!")

    OK ~> Content("application/json", """{"health":"alive"}""")
  }
}
