package com.socrata.tileserver
package util

import scala.collection.JavaConverters._
import scala.language.implicitConversions

import com.rojoma.json.v3.io.JsonReader.fromString
import com.rojoma.json.v3.codec.JsonEncode.toJValue
import com.vividsolutions.jts.geom.Geometry
import no.ecc.vectortile.VectorTileDecoder
import org.apache.commons.codec.binary.Base64
import org.mockito.Matchers
import org.mockito.Mockito.{verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, MustMatchers}

import com.socrata.thirdparty.geojson.GeoJson._
import com.socrata.thirdparty.geojson.{FeatureCollectionJson, GeoJsonBase}

import TileEncoder.Feature

// scalastyle:off import.grouping
class TileEncoderTest extends TestBase with MockitoSugar {
  implicit def byteToInt(pt: (Byte, Byte)): (Int, Int) = pt match {
    case (x: Byte, y: Byte) => (x.toInt, y.toInt)
  }

  def convert(feature: VectorTileDecoder.Feature): Feature = {
    val geom = feature.getGeometry
    val attrs = feature.getAttributes.asScala.toMap mapValues { o: Object =>
      fromString(o.toString)
    }

    (geom, attrs)
  }

  test("Features are encoded as bytes only if they are valid") {
    import gen.Points._

    forAll { (pt0: ValidPoint,
              pt1: ValidPoint,
              pt2: InvalidPoint,
              attr0: (String, String),
              attr1: (String, String)) =>
      val decoder = new VectorTileDecoder
      val valid = Set(feature(pt0, 1, Map(attr0)),
                      feature(pt1, 2, Map(attr1)))
      val invalid = Set(feature(pt2))
      val rawFeatures = FeatureCollectionJson(Seq(fJson(pt0, Map(attr0)),
                                                  fJson(pt1, Map(attr1)),
                                                  fJson(pt1, Map(attr1)),
                                                  fJson(pt2))): GeoJsonBase

      val bytes = new TileEncoder(invalid ++ valid, toJValue(rawFeatures)).bytes

      val decoded = decoder.decode(bytes)
      decoded.getLayerNames must equal (Set("main").asJava)

      val features = decoded.asScala.map(convert)

      features must have size (valid.size)
      valid foreach { features must contain (_) }
    }
  }

  test("Features are encoded as base64 bytes only if they are valid") {
    import gen.Points._

    forAll { (pt0: ValidPoint,
              pt1: ValidPoint,
              pt2: InvalidPoint,
              attr0: (String, String),
              attr1: (String, String)) =>
      val decoder = new VectorTileDecoder
      val valid = Set(feature(pt0, 1, Map(attr0)),
                      feature(pt1, 2, Map(attr1)))
      val invalid = Set(feature(pt2))
      val rawFeatures = FeatureCollectionJson(Seq(fJson(pt0, Map(attr0)),
                                                  fJson(pt1, Map(attr1)),
                                                  fJson(pt1, Map(attr1)),
                                                  fJson(pt2))): GeoJsonBase

      val base64 = new TileEncoder(invalid ++ valid, toJValue(rawFeatures)).base64
      val bytes = Base64.decodeBase64(base64)

      val decoded = decoder.decode(bytes)
      decoded.getLayerNames must equal (Set("main").asJava)

      val features = decoded.asScala.map(convert)

      features must have size (valid.size)
      valid foreach { features must contain (_)}
    }
  }

  // Behavior is undefined for invalid features.
  test("toString includes all valid features") {
    import gen.Points._

    forAll { (pt0: ValidPoint,
              pt1: ValidPoint,
              pt2: ValidPoint,
              attr0: (String, String),
              attr1: (String, String)) =>
      val (k0, v0) = attr0
      val (k1, v1) = attr1
      val features = Set(feature(pt0, 1, Map(attr0)),
                         feature(pt1, 2, Map(attr1)),
                         feature(pt2, 1))
      val rawFeatures = FeatureCollectionJson(Seq(fJson(pt0, Map(attr0)),
                                                  fJson(pt1, Map(attr1)),
                                                  fJson(pt1, Map(attr1)),
                                                  fJson(pt2))): GeoJsonBase

      val str = new TileEncoder(features, toJValue(rawFeatures)).toString

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
