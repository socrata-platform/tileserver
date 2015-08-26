package com.socrata.tileserver
package mocks

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.charset.{Charset, StandardCharsets}
import javax.activation.MimeType

import com.rojoma.json.v3.ast.JValue
import com.rojoma.json.v3.interpolation._
import com.rojoma.simplearm.v2.ResourceScope
import org.apache.commons.io.IOUtils

import com.socrata.http.client.Response
import com.socrata.http.common.util.Acknowledgeable

import EmptyResponse._
import UnusedSugar._
import util.GeoResponse

class EmptyResponse(ct: String = "application/vnd.geo+json")
    extends GeoResponse
    with Response {
  val resourceScope = Unused: ResourceScope
  val resultCode = 0
  val charset = StandardCharsets.UTF_8
  val streamCreated = true

  val headerNames = Set.empty[String]
  private lazy val bytes = IOUtils.toByteArray(inputStream())
  def payload: Array[Byte] = bytes

  def headers(name: String): Array[String] = Array.empty
  def inputStream(maxBetween: Long = 0): InputStream with Acknowledgeable =
    StringInputStream("{}")

  override val contentType: Option[MimeType] = Some(new MimeType(ct))
}

object EmptyResponse {
  val AnyMimeType: Option[MimeType] => Boolean = { mt => true }

  def apply(ct: String = "application/vnd.geo+json"): EmptyResponse =
    new EmptyResponse(ct)
}
