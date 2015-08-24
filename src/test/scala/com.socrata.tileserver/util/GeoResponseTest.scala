package com.socrata.tileserver
package util

import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}

import com.rojoma.json.v3.ast.JString
import com.rojoma.simplearm.v2.ResourceScope

// scalastyle:off no.whitespace.before.left.bracket
class GeoResponseTest extends TestBase with UnusedSugar {
  test("An empty list of coordinates rolls up correctly") {
    val resp = mocks.StaticGeoResponse(Iterator.empty)

    resp.features(Unused) must be (Set.empty)
  }

  test("A single coordinate rolls up correctly") {
    import gen.Points._

    forAll { pt: ValidPoint =>
      val resp = mocks.StaticGeoResponse(Iterator.single(fJson(pt)))

      resp.features(Unused) must equal (Set(feature(pt)))
    }
  }

  test("Unique coordinates are included when rolled up") {
    import gen.Points._

    forAll { pts: Set[ValidPoint] =>
      val coordinates = pts.map(fJson(_))
      val expected = pts.map(feature(_))
      val resp = mocks.StaticGeoResponse(coordinates.toIterator)

      val actual = resp.features(Unused)

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
      val resp = mocks.StaticGeoResponse(coordinates.toIterator)

      val actual = resp.features(Unused)

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
        val resp = mocks.StaticGeoResponse(coordinates.toIterator)

        val actual = resp.features(Unused)

        actual must equal (expected)
      }
    }
  }

  test("Features are unpacked with properties") {
    import gen.Points._

    forAll { pts: Seq[ValidPoint] =>
      // headerMap(0, Some("txt" -> SoQLText)), rows
      val upstream = mocks.MsgPackResponse(pts, Map("txt" -> "abcde"))

      val features = GeoResponse(upstream, Unused).rawFeatures.toSeq

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
      val features = GeoResponse(upstream, Unused).rawFeatures.toSeq

      features must have length (pts.size)
      features must equal (pts.map(fJson(_)))
    }
  }

  test("Retrieving features from a failed result throws") {
    forAll { rc: Int =>
      whenever (rc != ScOk) {
        val resp = new GeoResponse {
          val headerNames: Set[String] = Set.empty
          def headers(name: String): Array[String] = Array.empty
          val payload: Array[Byte] = Array.empty
          val resourceScope: ResourceScope = Unused
          val resultCode: Int = rc
        }

        an [IllegalStateException] must be thrownBy (resp.rawFeatures)
      }
    }
  }
}
