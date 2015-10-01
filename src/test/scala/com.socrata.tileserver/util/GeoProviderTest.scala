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
      val request = mocks.StaticRequest(param, Map(knownHeader,
                                                   unknownHeader,
                                                   "X-Socrata-Host" -> "geo.provider.test"))
      val info = RequestInfo(request, id, Unused, Unused, Unused)

      val filter = GeoProvider.filter(info.tile, info.geoColumn)
      val augmented = GeoProvider.augmentParams(info, filter)
      val expected = base.
        addPath("id").
        addPath(s"${id: String}.soqlpack").
        addHeader(ReqIdHeader -> info.requestId).
        addHeader("X-Socrata-Host" -> "geo.provider.test").
        addHeader(knownHeader).
        addParameters(augmented).
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

      GeoProvider(client).doQuery(info): Unit
    }
  }

  test("Augmenting parameters adds to where and select") {
    import gen.Alphanumerics._

    val otherKey = "$other"
    val whereKey = "$where"
    val selectKey = "$select"

    forAll {(rawOtherValue: Alphanumeric,
             whereParam: (Alphanumeric, Alphanumeric),
             selectParam: (Alphanumeric, Alphanumeric)) =>
      val otherValue: String = rawOtherValue
      val (whereBase, whereValue) = whereParam: (String, String)
      val (selectBase, selectValue) = selectParam: (String, String)

      val neither = mocks.StaticRequest(otherKey -> otherValue)
      val where = mocks.StaticRequest(whereKey -> whereBase)
      val select = mocks.StaticRequest(selectKey -> selectBase)

      neither.queryParameters must have size (1)
      val nParams = GeoProvider.augmentParams(reqInfo(neither,
                                                      geoColumn=selectValue),
                                              whereValue)
      nParams must have size (3)
      nParams(otherKey) must equal (otherValue)
      nParams(whereKey) must equal (whereValue)
      nParams(selectKey) must include (selectValue)

      val wParams = GeoProvider.augmentParams(reqInfo(neither ++ where,
                                                      geoColumn=selectValue),
                                              whereValue)
      wParams must have size (3)
      wParams(otherKey) must equal (otherValue)

      wParams(whereKey) must startWith (s"(${whereBase})")
      wParams(whereKey) must endWith (s"(${whereValue})")
      wParams(whereKey) must include regex ("\\s+and\\s+")

      wParams(selectKey) must include (selectValue)
      wParams(selectKey) must include ("simplify")


      val sParams = GeoProvider.augmentParams(reqInfo(neither ++ select,
                                                      geoColumn=selectValue),
                                              whereValue)
      sParams must have size (3)
      sParams(otherKey) must equal (otherValue)
      sParams(whereKey) must equal (whereValue)

      sParams(selectKey) must startWith (selectBase)
      sParams(selectKey) must include (selectValue)
      sParams(selectKey) must include ("simplify")
      sParams(selectKey) must include regex (",\\s*")

      val wsParams = GeoProvider.augmentParams(reqInfo(neither ++ where ++ select,
                                                       geoColumn=selectValue),
                                               whereValue)
      wsParams must have size (3)
      wsParams(otherKey) must equal (otherValue)

      wsParams(whereKey) must startWith (s"(${whereBase})")
      wsParams(whereKey) must endWith (s"(${whereValue})")
      wsParams(whereKey) must include regex ("\\s+and\\s+")

      wsParams(selectKey) must startWith (selectBase)
      wsParams(selectKey) must include (selectValue)
      wsParams(selectKey) must include regex (",\\s*")
    }
  }
}
