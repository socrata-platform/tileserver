package com.socrata.tileserver.mocks

import java.io.InputStream
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}
import scala.util.control.NoStackTrace

import com.socrata.http.common.util.Acknowledgeable

case class ThrowsResponse(message: String, override val resultCode: Int = ScOk)
    extends EmptyResponse {
  override def inputStream(ignored: Long = 0): InputStream with Acknowledgeable =
    throw new RuntimeException(message)
}
