package com.socrata.tileserver.mocks


import java.io.InputStream
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}

import com.rojoma.json.v3.ast.JValue
import com.socrata.http.common.util.Acknowledgeable

class StringResponse(val payload: String,
                     override val resultCode: Int = ScOk) extends EmptyResponse {
  override def inputStream(maxBetween: Long): InputStream with Acknowledgeable =
    StringInputStream(payload)
}

object StringResponse {
  def apply(payload: String, resultCode: Int = ScOk): StringResponse =
    new StringResponse(payload, resultCode)
  def apply(payload: JValue): StringResponse =
    new StringResponse(payload.toString)
}
