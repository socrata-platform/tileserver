package com.socrata.tileserver.mocks

import java.io.Closeable

import com.socrata.http.client._

class DynamicHttpClient(val respBuilder: (SimpleHttpRequest => Response)) extends HttpClient {
  val close: Unit = ()

  private def rawResp(r: Response) = new RawResponse with Closeable {
    val responseInfo = r
    val body = r.inputStream()
    val close = ()
  }

  def executeRawUnmanaged(req: SimpleHttpRequest): RawResponse with Closeable =
    rawResp(respBuilder(req))
}

object DynamicHttpClient {
  def apply(f: SimpleHttpRequest => Response): DynamicHttpClient =
    new DynamicHttpClient(f)
}
