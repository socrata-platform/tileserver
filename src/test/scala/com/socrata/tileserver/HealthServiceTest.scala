package com.socrata.tileserver

import org.scalatest.{FunSuite, MustMatchers}
import org.scalatest.prop.PropertyChecks

class HealthServiceTest extends FunSuite with MustMatchers with PropertyChecks {
  test("Hello, world!") {
    forAll { s: String =>
      s.length must be >= 0
    }
  }
}
