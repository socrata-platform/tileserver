package com.socrata.tileserver.mocks

import java.io.InputStream

import com.socrata.http.common.util.Acknowledgeable
import com.socrata.http.server.responses._

case class ThrowsResponse(ex: Exception, override val resultCode: Int)
    extends EmptyResponse {
  override def inputStream(ignored: Long = 0): InputStream with Acknowledgeable =
    throw ex
}

object ThrowsResponse {
  def apply(ex: Exception): ThrowsResponse = new ThrowsResponse(ex, OK.statusCode)
  def apply(message: String, resultCode: Int = OK.statusCode): ThrowsResponse =
    new ThrowsResponse(new RuntimeException(message), resultCode)
}
