package com.socrata.tileserver
package services

import org.joda.time.DateTime
import com.rojoma.json.v3.codec.JsonEncode
import org.slf4j.LoggerFactory
import buildinfo.BuildInfo

import com.socrata.http.server.implicits.httpResponseToChainedResponse
import com.socrata.http.server.HttpService
import com.socrata.http.server.responses.{OK, Json}
import com.socrata.http.server.routing.SimpleResource

object VersionService extends SimpleResource {
  val logger = LoggerFactory.getLogger(getClass)

  override def get: HttpService = { req =>
    logger.info("Alive!")

    OK ~>
      Json(JsonEncode.toJValue(
             Map("health" -> "alive",
                 "version" -> BuildInfo.version,
                 "scalaVersion" -> BuildInfo.scalaVersion,
                 "buildTime" -> new DateTime(BuildInfo.buildTime).toString())))
  }
}
