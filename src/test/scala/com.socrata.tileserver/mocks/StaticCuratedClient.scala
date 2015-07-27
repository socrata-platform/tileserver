package com.socrata.tileserver.mocks

import com.typesafe.config.Config
import org.mockito.Matchers.anyInt
import org.mockito.Mockito.{verify, when}
import org.scalatest.mock.MockitoSugar

import com.socrata.thirdparty.curator.{CuratedClientConfig, CuratedServiceClient}
import com.socrata.http.client.{RequestBuilder, Response, SimpleHttpRequest}
import com.socrata.thirdparty.curator.ServerProvider

import StaticCuratedClient._

class StaticCuratedClient(resp: Request => Response) extends MockitoSugar {
  val EmptyConfig = new CuratedClientConfig(mock[Config], "") {
    override val serviceName = ""
    override val connectTimeout = 0
    override val maxRetries = 0
  }

  val client = new CuratedServiceClient(mock[ServerProvider], EmptyConfig) {
    override def execute[T](request: Request, callback: Response => T): T = {
      callback(resp(request))
    }
  }
}

object StaticCuratedClient {
  type Request = RequestBuilder => SimpleHttpRequest

  def withReq(resp: Request => Response): CuratedServiceClient = new StaticCuratedClient(resp).client
  def apply(resp: () => Response): CuratedServiceClient = withReq { r => resp() }
  def apply(resp: Response): CuratedServiceClient = apply { () => resp }
}
