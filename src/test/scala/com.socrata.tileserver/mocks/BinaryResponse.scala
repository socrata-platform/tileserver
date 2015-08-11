package com.socrata.tileserver.mocks

import java.io.InputStream
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}

import com.socrata.http.common.util.Acknowledgeable

class BinaryResponse(payload: Array[Byte],
                     override val resultCode: Int = ScOk)
    extends EmptyResponse("application/octet-stream") {
  override def inputStream(maxBetween: Long): InputStream with Acknowledgeable =
    ByteInputStream(payload)
}

object BinaryResponse {
  def apply(payload: Array[Byte], resultCode: Int = ScOk) =
    new BinaryResponse(payload, resultCode)
}
