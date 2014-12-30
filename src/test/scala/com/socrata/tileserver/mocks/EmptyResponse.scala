package com.socrata.tileserver.mocks

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.charset.{Charset, StandardCharsets}
import javax.activation.MimeType

import com.rojoma.json.v3.ast.JValue
import com.rojoma.json.v3.interpolation._

import com.socrata.http.client.Response
import com.socrata.http.common.util.Acknowledgeable

class EmptyResponse extends Response {
  val resultCode: Int = 0
  val charset: Charset = StandardCharsets.UTF_8
  val streamCreated: Boolean = true
  val headerNames: Set[String] = Set.empty

  class AckByteArrayInputStream(bytes: Array[Byte])
      extends ByteArrayInputStream(bytes)
      with Acknowledgeable {
    override def acknowledge(): Unit = {}
  }

  def headers(name: String): Array[String] = Array.empty
  def inputStream(maxBetween: Long = 0): InputStream with Acknowledgeable =
    new AckByteArrayInputStream(Array.empty)

  override def jValue(ct: Option[MimeType] => Boolean = EmptyResponse.AnyMimeType,
                      max: Long = 0): JValue = EmptyResponse.EmptyJson

  override val contentType: Option[MimeType] =
    Some(new MimeType("application/json"))
}

object EmptyResponse {
  val AnyMimeType: Option[MimeType] => Boolean = { mt => true }
  val EmptyJson: JValue = json"{}"

  def apply(): EmptyResponse = new EmptyResponse
}
