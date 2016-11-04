package com.socrata.tileserver
package util

import org.scalatest.mock.MockitoSugar

import com.socrata.http.client.{RequestBuilder, Response}
import com.socrata.http.server.util.RequestId.ReqIdHeader
import com.socrata.soql.types.SoQLText
import com.socrata.testcommon

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
      val info = RequestInfo(request, id, Unused, Unused, Unused, None)

      val filter = GeoProvider.filter(info.tile, info.geoColumn, Unused)
      val augmented = GeoProvider.augmentParams(info, filter)
      val augmentedWithQueryTimeout = GeoProvider.addQueryTimeout(augmented, config.TileServerConfig.queryTimeout)

      val expected = base.
        addPath("id").
        addPath(s"${id: String}.soqlpack").
        addHeader(ReqIdHeader -> info.requestId).
        addHeader("X-Socrata-Federation" -> "Honey Badger").
        addHeader("X-Socrata-Host" -> "geo.provider.test").
        addHeader(knownHeader).
        addParameters(augmentedWithQueryTimeout).
        get.builder

      val client = testcommon.mocks.StaticCuratedClient { request =>
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

  test("Augmenting parameters adds to select, where and group") {
    import gen.Alphanumerics._

    val otherKey = "$other"
    val groupKey = "$group"
    val whereKey = "$where"
    val selectKey = "$select"

    forAll {(rawOtherValue: Alphanumeric,
             selectParam: (Alphanumeric, Alphanumeric),
             whereParam: (Alphanumeric, Alphanumeric),
             groupParam: Alphanumeric) =>
      val otherValue: String = rawOtherValue
      val (selectBase, selectValue) = selectParam: (String, String)
      val (whereBase, whereValue) = whereParam: (String, String)
      val groupBase = groupParam: String

      val mondara = mocks.StaticRequest('$' + "mondara" -> "true")

      val neither = mocks.StaticRequest(otherKey -> otherValue)
      val select = mocks.StaticRequest(selectKey -> selectBase)
      val where = mocks.StaticRequest(whereKey -> whereBase)
      val group = mocks.StaticRequest(groupKey -> groupBase)

      neither.queryParameters must have size (1)
      val nParams = GeoProvider.augmentParams(reqInfo(neither ++ mondara,
                                                      geoColumn=selectValue),
                                              whereValue)
      nParams must have size (4)
      nParams(selectKey) must include (selectValue)
      nParams(selectKey) must include ("snap_to_grid")
      nParams(whereKey) must equal (whereValue)
      nParams(otherKey) must equal (otherValue)
      nParams(groupKey) must include ("snap_to_grid")

      val nfParams = GeoProvider.augmentParams(reqInfo(neither,
                                                       geoColumn=selectValue),
                                               whereValue)
      nfParams must have size (3)
      nfParams(selectKey) must equal (selectValue)
      nfParams(whereKey) must equal (whereValue)
      nfParams(otherKey) must equal (otherValue)

      val sParams = GeoProvider.augmentParams(reqInfo(neither ++ select ++ mondara,
                                                      geoColumn=selectValue),
                                              whereValue)
      sParams must have size (4)
      sParams(selectKey) must startWith (s"$selectBase,")
      sParams(selectKey) must include (selectValue)
      sParams(selectKey) must include ("snap_to_grid")

      sParams(otherKey) must equal (otherValue)
      sParams(whereKey) must equal (whereValue)

      sParams(groupKey) must include ("snap_to_grid")

      val wParams = GeoProvider.augmentParams(reqInfo(neither ++ where ++ mondara,
                                                      geoColumn=selectValue),
                                              whereValue)
      wParams must have size (4)
      wParams(otherKey) must equal (otherValue)

      wParams(selectKey) must include (selectValue)
      wParams(selectKey) must include ("snap_to_grid")

      wParams(whereKey) must startWith (s"(${whereBase}) and")
      wParams(whereKey) must endWith (s"(${whereValue})")

      wParams(groupKey) must include ("snap_to_grid")

      val gParams = GeoProvider.augmentParams(reqInfo(neither ++ group ++ mondara,
                                                      geoColumn=selectValue),
                                              whereValue)
      gParams must have size (4)
      gParams(otherKey) must equal (otherValue)

      gParams(selectKey) must include (selectValue)
      gParams(selectKey) must include ("snap_to_grid")

      gParams(groupKey) must startWith (s"(${groupBase}),")
      gParams(groupKey) must include ("snap_to_grid")

      val allParams = GeoProvider.augmentParams(
        reqInfo(neither ++ where ++ select ++ group ++ mondara, geoColumn=selectValue),
        whereValue)

      allParams must have size (4)
      allParams(otherKey) must equal (otherValue)

      allParams(selectKey) must startWith (s"$selectBase,")
      allParams(selectKey) must include (selectValue)
      allParams(selectKey) must include ("snap_to_grid")

      allParams(whereKey) must startWith (s"(${whereBase}) and ")
      allParams(whereKey) must endWith (s"(${whereValue})")

      allParams(groupKey) must startWith (s"(${groupBase}),")
      allParams(groupKey) must include ("snap_to_grid")

      val allfParams = GeoProvider.augmentParams(
        reqInfo(neither ++ where ++ select ++ group, geoColumn=selectValue),
        whereValue)

      allfParams must have size (4)
      allfParams(otherKey) must equal (otherValue)

      allfParams(selectKey) must equal (s"$selectBase, $selectValue")
      allfParams(whereKey) must equal (s"(${whereBase}) and (${whereValue})")
      allfParams(groupKey) must equal (s"${groupBase}")
    }
  }

  test("GeoProvider adds info.overscan to $where if present") {
    val resp = mock[Response]
    val base = RequestBuilder("mock.socrata.com")

    forAll { os: Int =>
      val info = mocks.PngInfo(os)
      val expected = GeoProvider.filter(info.tile, info.geoColumn, os)

      val client = testcommon.mocks.StaticCuratedClient { request =>
        val actual = request(base).builder
        val query = actual.query.toMap

        // Assertions are in here, since we only care about what the client sees.
        query('$' + "where") must equal (expected)

        resp
      }

      GeoProvider(client).doQuery(info): Unit
    }
  }

  test("GeoProvider adds overscan of zero to $where if absent") {
    val resp = mock[Response]
    val base = RequestBuilder("mock.socrata.com")

    val info = mocks.PngInfo(Unused, None, None)
    val expected = GeoProvider.filter(info.tile, info.geoColumn, 0)

    val client = testcommon.mocks.StaticCuratedClient { request =>
      val actual = request(base).builder
      val query = actual.query.toMap

      // Assertions are in here, since we only care about what the client sees.
      query('$' + "where") must equal (expected)

      resp
    }

    GeoProvider(client).doQuery(info): Unit
  }

  test("filter uses overscan to adjust corners") {
    import gen.QuadTiles._

    forAll { (tile: QuadTile, geoColumn: String, os: Int) =>
      val actual = GeoProvider.filter(tile, geoColumn, os)

      tile.corners(os).foreach { case (lat, lon) =>
        actual must include (lat.toString)
        actual must include (lon.toString)
      }
    }
  }
}
