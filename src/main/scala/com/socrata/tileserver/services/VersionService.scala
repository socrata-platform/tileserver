package com.socrata.tileserver.services

import com.rojoma.json.v3.interpolation._
import org.slf4j.LoggerFactory

import com.socrata.http.server.implicits.httpResponseToChainedResponse
import com.socrata.http.server.HttpService
import com.socrata.http.server.responses.{OK, Json}
import com.socrata.http.server.routing.SimpleResource

object VersionService extends SimpleResource {
  val logger = LoggerFactory.getLogger(getClass)

  override def get: HttpService = { req =>
    logger.info("Alive!")

    OK ~> Json(json"""{health:"alive", version:"0.1.0"}""") // TODO: Do this dynamically.
  }
}
