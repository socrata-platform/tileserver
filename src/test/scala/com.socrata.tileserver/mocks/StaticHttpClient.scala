package com.socrata.tileserver.mocks

import com.socrata.http.client.{HttpClient, Response}
import com.socrata.http.server.responses._

object StaticHttpClient {
  def apply(resp: Response): HttpClient = new DynamicHttpClient(_ => resp)
  def apply(resp: String, resultCode: Int = OK.statusCode): HttpClient =
    StaticHttpClient(new StringResponse(resp, resultCode))
}
