package com.socrata.tileserver.mocks

import org.mockito.Mockito.{verify, when}
import org.scalatest.mock.MockitoSugar
import org.mockito.Matchers.anyInt

import com.socrata.backend.client.CoreServerClient
import com.socrata.backend.config.CoreServerClientConfig
import com.socrata.http.client.{RequestBuilder, Response, SimpleHttpRequest}
import com.socrata.thirdparty.curator.ServerProvider

import StaticClient._

class StaticClient(resp: Request => Response) extends MockitoSugar {
  val EmptyConfig = new CoreServerClientConfig {
    def connectTimeoutSec: Int = 0
    def maxRetries: Int = 0
  }

  val client = new CoreServerClient(mock[ServerProvider], EmptyConfig) {
    override def execute[T](request: Request, callback: Response => T): T = {
      callback(resp(request))
    }
  }
}

object StaticClient {
  type Request = RequestBuilder => SimpleHttpRequest

  def withReq(resp: Request => Response): CoreServerClient = new StaticClient(resp).client
  def apply(resp: () => Response): CoreServerClient = withReq { r => resp() }
  def apply(resp: Response): CoreServerClient = apply { () => resp }
}
