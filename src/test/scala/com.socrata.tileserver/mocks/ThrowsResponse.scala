package com.socrata.tileserver.mocks

import java.io.InputStream
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}
import scala.util.control.NoStackTrace

import com.socrata.http.common.util.Acknowledgeable

case class ThrowsResponse(ex: Exception, override val resultCode: Int)
    extends EmptyResponse {
  override def inputStream(ignored: Long = 0): InputStream with Acknowledgeable =
    throw ex
}

object ThrowsResponse {
  def apply(ex: Exception): ThrowsResponse = new ThrowsResponse(ex, ScOk)
  def apply(message: String, resultCode: Int = ScOk): ThrowsResponse =
    new ThrowsResponse(new RuntimeException(message), resultCode)
}
