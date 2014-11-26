package com.socrata.tileserver
package util

import java.io.InputStream
import javax.servlet.http.HttpServletResponse

import org.mockito.Mockito.{verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, MustMatchers}

import com.socrata.http.client.Response
import com.socrata.http.common.util.Acknowledgeable

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

  test("Extensions return error on encoder failure") {
    val clientResp = mock[Response]
    val noneEncoder: Encoder = _ => None

    Extensions.values filter (_ != Json) foreach { ext: Extension =>
      val outputStream = new util.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      ext(noneEncoder, clientResp)(resp)

      verify(resp).setStatus(500)
      verify(resp).setContentType("application/json")

      outputStream.getLowStr must include ("invalid")
      outputStream.getLowStr must include  ("json")
    }
  }

  test("DefaultResponse includes CORS header") {
    val resp = mock[HttpServletResponse]

    DefaultResponse(resp)

    verify(resp).setStatus(200)
    verify(resp).setHeader("Access-Control-Allow-Origin", "*")
  }

  test("Extensions include CORS header on success") {
    val inputStream: InputStream with Acknowledgeable =
      new InputStream with Acknowledgeable {
        override def acknowledge() = {}
        override def read() = -1
      }

    val clientResp = mock[Response]
    when(clientResp.inputStream()).thenReturn(inputStream)

    val emptyEncoder: Encoder = _ => Some(Array.empty)

    Extensions.values foreach { ext: Extension =>
      val outputStream = new util.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      ext(emptyEncoder, clientResp)(resp)

      verify(resp).setStatus(200)
      verify(resp).setHeader("Access-Control-Allow-Origin", "*")
    }
  }

  test("Pbf sends bytes from encoder") {
    val clientResp = mock[Response]

    forAll { bytes: Array[Byte] =>
      val outputStream = new util.ByteArrayServletOutputStream
      val resp = outputStream.responseFor
      val encoder: Encoder = r => Some(bytes)

      Pbf(encoder, clientResp)(resp)

      verify(resp).setStatus(200)
      verify(resp).setHeader("Access-Control-Allow-Origin", "*")

      outputStream.getBytes must equal (bytes)
    }
  }

  test("B64Pbf sends base 64 encoded bytes from encoder")(pending)
  test("Txt")(pending)
  test("Json")(pending)
}
