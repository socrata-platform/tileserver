package com.socrata.tileserver
package util

import com.vividsolutions.jts.geom.Coordinate
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, MustMatchers}

import CoordinateMapper.Size

class CoordinateMapperTest extends TestBase {
  test("lon maps x to longitude") {
    // scalastyle:off magic.number
    val mapper14 = CoordinateMapper(14)
    mapper14.lon(1076992) must equal (-87.56103515625000)
    mapper14.lon(1077247) must equal (-87.53914833068848)

    val mapper5 = CoordinateMapper(5)
    mapper5.lon(2048) must equal (-90.0)
    mapper5.lon(2303) must equal (-78.7939453125)
    // scalastyle:on magic.number
  }

  test("lon maps y to longitude") {
    // scalastyle:off magic.number
    val mapper14 = CoordinateMapper(14)
    mapper14.lat(2632192) must equal (41.672911819602085)
    mapper14.lat(2632447) must equal (41.689258164829670)

    val mapper5 = CoordinateMapper(5)
    mapper5.lat(5120) must equal (40.97989806962013)
    mapper5.lat(5375) must equal (48.89361536148018)
    // scalastyle:on magic.number
  }

  test("tilePx(lon, lat) maps correctly to pixels") {
    // scalastyle:off magic.number
    val mapper14 = CoordinateMapper(14)
    mapper14.tilePx(-87.539383, 41.681059) must equal ((252, 129))
    mapper14.tilePx(-87.545220, 41.683555) must equal ((184, 90))
    mapper14.tilePx(-87.541167, 41.688135) must equal ((231, 19))
    mapper14.tilePx(-87.559501, 41.682697) must equal ((18, 103))
    mapper14.tilePx(-87.557169, 41.688140) must equal ((45, 18))

    val mapper5 = CoordinateMapper(5)
    mapper5.tilePx(-87.676410, 41.776204) must equal ((53, 232))
    mapper5.tilePx(-87.560088, 41.753145) must equal ((56, 233))
    mapper5.tilePx(-87.667603, 42.011423) must equal ((53, 225))
    // scalastyle:on magic.number
  }


  test("tilePx(lon, lat) maps points into pixel space") {
    forAll { (zoom: Int, x: Int, y: Int) =>
      val mapper = CoordinateMapper(zoom)
      val (tx, ty) = mapper.px(x, y)

      (tx % Size, ty % Size) must equal (mapper.tilePx(x, y))
    }
  }

  test("tilePx(coordinate) returns the same values as tilePx(x, y)") {
    forAll { (zoom: Int, x: Int, y: Int) =>
      val mapper = CoordinateMapper(zoom)

      val (tx, ty) = mapper.tilePx(x, y)
      new Coordinate(tx, ty) must equal (mapper.tilePx(new Coordinate(x, y)))
    }
  }
}
