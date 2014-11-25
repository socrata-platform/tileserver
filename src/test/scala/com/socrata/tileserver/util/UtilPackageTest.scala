package com.socrata.tileserver
package util

import javax.servlet.http.HttpServletResponse

import org.mockito.Mockito.{verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, MustMatchers}

class UtilPackageTest
    extends FunSuite
    with MustMatchers
    with PropertyChecks
    with MockitoSugar {
  test("InvalidJson returns appropriate error message") {
    val outputStream = new util.ByteArrayServletOutputStream
    val resp = outputStream.responseFor

    InvalidJson(resp)

    verify(resp).setStatus(500)
    verify(resp).setContentType("application/json")

    outputStream.getLowStr must include ("invalid")
    outputStream.getLowStr must include  ("json")
  }

  test("DefaultResponse includes CORS header") {
    val resp = mock[HttpServletResponse]

    DefaultResponse(resp)

    verify(resp).setStatus(200)
    verify(resp).setHeader("Access-Control-Allow-Origin", "*")
  }
}
