package com.socrata.tileserver
package services

import javax.servlet.http.HttpServletResponse.{SC_BAD_REQUEST => ScBadRequest}
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

  def escape(s: String): String = s.toCharArray map { _.toInt } mkString ","

  test("Bad request must include message and cause") {
    forAll { (message: String, causeMessage: String) =>
      val outputStream = new util.ByteArrayServletOutputStream
      val resp = outputStream.responseFor
      val cause = new NoStackTrace {
        override def getMessage: String = causeMessage
      }

      ImageQueryService.badRequest(message, cause).apply(resp)

      verify(resp).setStatus(ScBadRequest)
      verify(resp).setContentType("application/json; charset=UTF-8")

      outputStream.getLowStr must include ("message")
      escape(outputStream.getString) must include (escape(jStr(message)))
      outputStream.getLowStr must include ("cause")
      escape(outputStream.getString) must include (escape(jStr(causeMessage)))
    }
  }

  test("Bad request must include message and info") {
    forAll { (message: String, info: String) =>
      val outputStream = new util.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      ImageQueryService.badRequest(message, info).apply(resp)

      verify(resp).setStatus(ScBadRequest)
      verify(resp).setContentType("application/json; charset=UTF-8")

      outputStream.getLowStr must include ("message")
      escape(outputStream.getString) must include (escape(jStr(message)))
      outputStream.getLowStr must include ("info")
      escape(outputStream.getString) must include (escape(jStr(info)))
    }
  }
}
