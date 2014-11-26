package com.socrata.tileserver
package services

import scala.util.control.NoStackTrace

import com.rojoma.json.v3.ast.JString
import org.mockito.Mockito.{verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, MustMatchers}
import org.slf4j.Logger

import com.socrata.http.server.HttpRequest

class ImageQueryServiceTest
    extends FunSuite
    with MustMatchers
    with PropertyChecks
    with MockitoSugar {
  implicit val logger: Logger = mock[Logger]

  def jStr(s: String): String = JString(s).toString

  test("Bad request must include message and cause") {
    forAll { (message: String, causeMessage: String) =>
      val outputStream = new util.ByteArrayServletOutputStream
      val resp = outputStream.responseFor
      val cause = new NoStackTrace {
        override def getMessage = causeMessage
      }

      ImageQueryService.badRequest(message, cause).apply(resp)

      verify(resp).setStatus(400)
      verify(resp).setContentType("application/json; charset=UTF-8")

      outputStream.getLowStr must include ("message")
      outputStream.getString must include (jStr(message))
      outputStream.getLowStr must include ("cause")
      outputStream.getString must include (jStr(causeMessage))
    }
  }

  test("Bad request must include message and info") {
    forAll { (message: String, info: String) =>
      val outputStream = new util.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      ImageQueryService.badRequest(message, info).apply(resp)

      verify(resp).setStatus(400)
      verify(resp).setContentType("application/json; charset=UTF-8")

      outputStream.getLowStr must include ("message")
      outputStream.getString must include (jStr(message))
      outputStream.getLowStr must include ("info")
      outputStream.getString must include (jStr(info))
    }
  }
}
