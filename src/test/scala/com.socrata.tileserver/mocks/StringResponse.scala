package com.socrata.tileserver.mocks


import java.io.InputStream
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}

import com.rojoma.json.v3.ast.JValue
import com.socrata.http.common.util.Acknowledgeable

class StringResponse(val message: String,
                     override val resultCode: Int = ScOk) extends EmptyResponse {
  override def inputStream(maxBetween: Long): InputStream with Acknowledgeable =
    StringInputStream(message)
}

object StringResponse {
  def apply(message: String, resultCode: Int = ScOk): StringResponse =
    new StringResponse(message, resultCode)
  def apply(message: JValue): StringResponse =
    new StringResponse(message.toString)
}
