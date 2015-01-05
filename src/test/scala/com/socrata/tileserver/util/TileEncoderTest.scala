package com.socrata.tileserver
package util

import scala.collection.JavaConverters._
import scala.collection.immutable.ListSet

import com.rojoma.json.v3.ast.JObject
import no.ecc.vectortile.VectorTileDecoder
import org.mockito.Matchers
import org.mockito.Mockito.{verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, MustMatchers}

import TileEncoder.Feature

class TileEncoderTest extends TestBase with MockitoSugar {
  def b2i(pt: (Byte, Byte)): (Int, Int) = {
    val (x, y) = pt

    (x.toInt, y.toInt)
  }

  test("bytes encodes features properly") (pending)

  {
    forAll { (pt0: (Byte, Byte),
              pt1: (Byte, Byte),
              kv0: (String, String),
              kv1: (String, String),
              layer: String) =>
      val features = ListSet(feature(b2i(pt0), 1, Map(kv0)),
                             feature(b2i(pt1), 2, Map(kv1)))

      val bytes = TileEncoder(features).bytes

      // decode, but assert 0 layers if no features with positive points.
    }
  }

  test("base64 encodes features properly") (pending)

  test("toString includes all features") {
    forAll { (pt0: (Int, Int),
              pt1: (Int, Int),
              pt2: (Int, Int),
              kv0: (String, String),
              kv1: (String, String)) =>
      val (k0, v0) = kv0
      val (k1, v1) = kv1
      val features = Set(feature(pt0, 1, Map(kv0)),
                         feature(pt1, 2, Map(kv1)),
                         feature(pt2, 1))

      val str = TileEncoder(features).toString

      features foreach { case (geom, _) =>
        str must include (geom.toString)
      }

      str must include (encode(k0))
      str must include (encode(v0))
      str must include (encode(k1))
      str must include (encode(v1))
    }
  }
}
