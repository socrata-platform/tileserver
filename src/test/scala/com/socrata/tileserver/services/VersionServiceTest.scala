package com.socrata.tileserver
package services

import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}

import org.mockito.Mockito.{verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, MustMatchers}

import com.socrata.http.server.HttpRequest

class VersionServiceTest
    extends FunSuite
    with MustMatchers
    with MockitoSugar {
  test("Endpoint must return health = alive") {
    val req = mock[HttpRequest]
    val outputStream = new mocks.ByteArrayServletOutputStream
    val resp = outputStream.responseFor

    VersionService.get(req)(resp)

    verify(resp).setStatus(ScOk)
    verify(resp).setContentType("application/json; charset=UTF-8")

    outputStream.getLowStr must include ("health")
    outputStream.getLowStr must include ("alive")
  }
}
