package com.socrata.tileserver.mocks


import java.io.InputStream
import com.rojoma.json.v3.ast.JValue

import com.socrata.http.common.util.Acknowledgeable
import com.socrata.http.server.responses._

class StringResponse(val message: String,
                     override val resultCode: Int = OK.statusCode) extends EmptyResponse {
  override def inputStream(maxBetween: Long): InputStream with Acknowledgeable =
    StringInputStream(message)
}

object StringResponse {
  def apply(message: String, resultCode: Int = OK.statusCode): StringResponse =
    new StringResponse(message, resultCode)
  def apply(message: JValue): StringResponse =
    new StringResponse(message.toString)
}
