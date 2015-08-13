package com.socrata.tileserver
package util

import com.rojoma.json.v3.ast.JString
import org.scalatest.mock.MockitoSugar

import com.socrata.http.client.{RequestBuilder, Response}
import com.socrata.http.server.util.RequestId.{RequestId, ReqIdHeader}
import com.socrata.soql.types.SoQLText

class GeoProviderTest extends TestBase with UnusedSugar with MockitoSugar {
  test("Headers and parameters are correct") {
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

      val expected = base.
        addPath("id").
        addPath(s"${id: String}.soqlpack").
        addHeader(ReqIdHeader -> reqId).
        addHeader(knownHeader).
        addParameter(param).
        get.builder

      val client = mocks.StaticCuratedClient.withReq { request =>
        val actual = request(base).builder

        actual.url must equal (expected.url)
        actual.method must equal (expected.method)
        actual.query.toSet must equal (expected.query.toSet)
        actual.headers.toSet must equal (expected.headers.toSet)

        resp
      }

      GeoProvider(client).
        doQuery(reqId, request, id, Map(param), Unused): Unit
    }
  }

  test("An empty list of coordinates rolls up correctly") {
    GeoProvider.rollup(Unused, Iterator.empty) must be (Set.empty)
  }

  test("A single coordinate rolls up correctly") {
    import gen.Points._

    forAll { pt: ValidPoint =>
      GeoProvider.rollup(Unused, Iterator.single(fJson(pt))) must equal (Set(feature(pt)))
    }
  }

  test("Unique coordinates are included when rolled up") {
    import gen.Points._

    forAll { pts: Set[ValidPoint] =>
      val coordinates = pts.map(fJson(_))
      val expected = pts.map(feature(_))
      val actual = GeoProvider.rollup(Unused, coordinates.toIterator)

      actual must equal (expected)
    }
  }

  test("Coordinates have correct counts when rolled up") {
    import gen.Points._

    forAll { uniquePts: Set[ValidPoint] =>
      val pts = uniquePts.toSeq
      val dupes = pts ++
        (if (pts.isEmpty) pts else pts(0) +: Seq(pts(pts.length - 1)))

      val coordinates = dupes.map(fJson(_))
      val expected = dupes.
        groupBy(identity).
        mapValues(_.size).map(feature(_)).toSet

      val actual = GeoProvider.rollup(Unused, coordinates.toIterator)

      actual must equal (expected)
    }
  }

  test("Coordinates with unique properties are not rolled up") {
    import gen.Points._

    forAll { (pt0: ValidPoint,
              pt1: ValidPoint,
              prop0: (String, String),
              prop1: (String, String)) =>
      val (k0, _) = prop0
      val (k1, _) = prop1

      whenever (pt0 != pt1 && prop0 != prop1 && k0 != k1) {
        val coordinates = Seq(fJson(pt0, Map(prop0)),
                              fJson(pt0, Map(prop0, prop1)),
                              fJson(pt0, Map(prop1)),
                              fJson(pt1, Map(prop1)),
                              fJson(pt1, Map(prop1)))
        val expected = Set(feature(pt0, 1, Map(prop0)),
                           feature(pt0, 1, Map(prop0, prop1)),
                           feature(pt0, 1, Map(prop1)),
                           feature(pt1, 2, Map(prop1)))

        val actual = GeoProvider.rollup(Unused, coordinates.toIterator)

        actual must equal (expected)
      }
    }
  }

  test("Features are unpacked with properties") {
    import gen.Points._

    forAll { pts: Seq[ValidPoint] =>
      // headerMap(0, Some("txt" -> SoQLText)), rows
      val upstream = mocks.MsgPackResponse(pts, Map("txt" -> "abcde"))

      val maybeResult = GeoProvider.unpackFeatures(Unused)(upstream)
      maybeResult must be a ('success)

      val iter = maybeResult.get
      val features = iter.toSeq

      features must have length (pts.size)
      for { i <- 0 until pts.length } {
        features(i).geometry must equal (point(pts(i)))
        features(i).properties must equal (Map("txt" -> JString("abcde")))
      }
    }
  }

  test("Features are successfully unpacked") {
    import gen.Points._

    forAll { pts: Seq[ValidPoint] =>
      val upstream = mocks.MsgPackResponse(pts)
      val maybeResult = GeoProvider.unpackFeatures(Unused)(upstream)
      maybeResult must be a ('success)

      val iter = maybeResult.get
      val features = iter.toSeq

      features must have length (pts.size)
      features must equal (pts.map(fJson(_)))
    }
  }
}
