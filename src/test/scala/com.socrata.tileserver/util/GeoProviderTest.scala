package com.socrata.tileserver
package util

import org.scalatest.mock.MockitoSugar

import com.socrata.http.server.util.RequestId.{RequestId, ReqIdHeader}
import com.socrata.http.client.{RequestBuilder, Response}

class GeoProviderTest extends TestBase with UnusedSugar with MockitoSugar {
  test("Headers and parameters are correct when making a geo-json query") {
    import gen.Headers._

    forAll { (reqId: RequestId,
              id: String,
              param: (String, String),
              knownHeader: IncomingHeader,
              unknownHeader: UnknownHeader) =>
      val base = RequestBuilder("mock.socrata.com")
      val request = mocks.StaticRequest(param, Map(knownHeader, unknownHeader))

      val expected = base.
        path(Seq("id", s"$id.geojson")).
        addHeader(ReqIdHeader -> reqId).
        addHeader(knownHeader).
        query(Map(param)).
        get.builder

      val client = mocks.StaticCuratedClient.withReq { request =>
        val actual = request(base).builder

        actual.url must equal (expected.url)
        actual.method must equal (expected.method)
        actual.query.toSet must equal (expected.query.toSet)
        actual.headers.toSet must equal (expected.headers.toSet)

        mock[Response]
      }

      GeoProvider(client).
        doQuery(reqId, request, id, Map(param), false, Unused): Unit
    }
  }
}
