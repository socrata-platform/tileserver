package com.socrata.tileserver.mocks

import java.io.InputStream
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}

import com.socrata.http.common.util.Acknowledgeable

case class StringResponse(val payload: String,
                          override val resultCode: Int = ScOk) extends EmptyResponse {
  override def inputStream(maxBetween: Long): InputStream with Acknowledgeable =
    new AckByteArrayInputStream(payload.getBytes)
}
