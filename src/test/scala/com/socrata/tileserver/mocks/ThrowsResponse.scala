package com.socrata.tileserver.mocks

import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}
import scala.util.control.NoStackTrace

case class ThrowsResponse(message: String, override val resultCode: Int = ScOk)
    extends EmptyResponse {
  override def inputStream(ignored: Long = 0) = throw new NoStackTrace {
    override def getMessage = message
  }
}
