package com.socrata.tileserver.mocks

import org.mockito.Mockito.{verify, when}
import org.scalatest.mock.MockitoSugar
import org.mockito.Matchers.anyInt

import com.socrata.backend.client.CoreServerClient
import com.socrata.backend.config.CoreServerClientConfig
import com.socrata.http.client.{RequestBuilder, Response, SimpleHttpRequest}
import com.socrata.thirdparty.curator.ServerProvider

import StringClient.EmptyConfig

class StringClient(statusCode: Int, body: String) extends MockitoSugar {
  val client = new CoreServerClient(mock[ServerProvider], EmptyConfig) {
    override def execute[T](request: RequestBuilder => SimpleHttpRequest,
                            callback: Response => T): T = {
      val resp = mock[Response]
      when(resp.resultCode).thenReturn(statusCode)
      when(resp.inputStream(anyInt())).thenReturn(StringInputStream(body))

      callback(resp)
    }
  }
}

object StringClient {
  val EmptyConfig = new CoreServerClientConfig {
    def connectTimeoutSec: Int = 0
    def maxRetries: Int = 0
  }

  def apply(statusCode: Int, body: String): CoreServerClient =
    new StringClient(statusCode, body).client
}
