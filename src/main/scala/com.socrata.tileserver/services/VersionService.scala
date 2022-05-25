package com.socrata.tileserver
package services

import org.joda.time.DateTime
import com.rojoma.json.v3.codec.JsonEncode
import org.slf4j.LoggerFactory
import buildinfo.BuildInfo

import com.socrata.http.server.HttpService
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.routing.SimpleResource

/** Returns the version of this service. */
object VersionService extends SimpleResource {
  private val logger = LoggerFactory.getLogger(getClass)

  /** Handle the request. */
  override def get: HttpService = { req =>
    logger.info("Alive!")

    OK ~>
      Json(JsonEncode.toJValue(
             Map("health" -> "alive",
                 "version" -> BuildInfo.version,
                 "scalaVersion" -> BuildInfo.scalaVersion,
                 "buildTime" -> new DateTime(BuildInfo.builtAtMillis).toString())))
  }
}
