package com.socrata.tileserver
package services

import javax.servlet.ServletOutputStream
import javax.servlet.http.HttpServletResponse

import org.mockito.Mockito.{verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, MustMatchers}

import com.socrata.http.server.HttpRequest

class HealthServiceTest
    extends FunSuite
    with MustMatchers
    with MockitoSugar {
  test("Endpoint must report liveliness") {

  }
}
