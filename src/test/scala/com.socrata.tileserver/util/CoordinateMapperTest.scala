package com.socrata.tileserver
package util

import com.vividsolutions.jts.geom.Coordinate
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, MustMatchers}

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
}
