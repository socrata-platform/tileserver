package com.socrata.tileserver
package util

import org.scalatest.mock.MockitoSugar

import com.socrata.http.client.{RequestBuilder, Response}
import com.socrata.http.server.util.RequestId.ReqIdHeader
import com.socrata.soql.types.SoQLText

class GeoProviderTest extends TestBase with UnusedSugar with MockitoSugar {
  test("Headers and parameters are correct") {
    implicit val generatorDrivenConfig = PropertyCheckConfig(minSuccessful = 5)

    import gen.Headers._
    import gen.Alphanumerics._
    import gen.ShortStrings._

    val resp = mock[Response]
    val base = RequestBuilder("mock.socrata.com")

    forAll { (reqId: Alphanumeric,
              id: Alphanumeric,
              param: (ShortString, ShortString),
              knownHeader: IncomingHeader,
              unknownHeader: UnknownHeader) =>
      val request = mocks.StaticRequest(param, Map(knownHeader, unknownHeader))
      val info = RequestInfo(request, id, Unused, Unused, Unused)

      val expected = base.
        addPath("id").
        addPath(s"${id: String}.soqlpack").
        addHeader(ReqIdHeader -> info.requestId).
        addHeader(knownHeader).
        addParameter(param).
        get.builder

      val client = mocks.StaticCuratedClient.withReq { request =>
        val actual = request(base).builder

        // Assertions are in here, since we only care about what the client sees.
        actual.url must equal (expected.url)
        actual.method must equal (expected.method)
        actual.query.toSet must equal (expected.query.toSet)
        actual.headers.toSet must equal (expected.headers.toSet)

        resp
      }

      GeoProvider(client).doQuery(info, Map(param)): Unit
    }
  }
}
