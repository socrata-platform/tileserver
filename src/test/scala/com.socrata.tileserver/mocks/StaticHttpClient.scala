package com.socrata.tileserver.mocks

import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}
import com.socrata.http.client.{HttpClient, Response}

object StaticHttpClient {
  def apply(resp: Response): HttpClient = new DynamicHttpClient(_ => resp)
  def apply(resp: String, resultCode: Int = ScOk): HttpClient =
    StaticHttpClient(new StringResponse(resp, resultCode))
}
