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
  test("Endpoint must return health = alive") {
    val req = mock[HttpRequest]
    val resp = mock[HttpServletResponse]
    val outputStream = new util.ByteArrayServletOutputStream
    when(resp.getOutputStream()).thenReturn(outputStream)

    HealthService.get(req)(resp)

    verify(resp).setStatus(200)
    verify(resp).setContentType("application/json")
    verify(resp).getOutputStream()

    outputStream.getString must equal ("""{"health":"alive"}""")
  }
}
