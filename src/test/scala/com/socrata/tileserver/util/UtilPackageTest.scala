package com.socrata.tileserver
package util

import java.io.InputStream
import javax.activation.MimeType
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponse.{SC_INTERNAL_SERVER_ERROR => ScInternalServerError}
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}

import com.rojoma.json.v3.io.JsonReader
import org.apache.commons.codec.binary.Base64
import org.mockito.Mockito.{verify, when}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
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
  test("JsonP matches application/json and application/vnd.geo+json") {
    JsonP(Some(new MimeType("application/json"))) must be (true)
    JsonP(Some(new MimeType("application/vnd.geo+json"))) must be (true)
  }

  test("JsonP doesn't match text/plain or None") {
    JsonP(Some(new MimeType("text/plain"))) must be (false)
    JsonP(None) must be (false)
  }

  test("InvalidJson returns appropriate error message") {
    val outputStream = new mocks.ByteArrayServletOutputStream
    val resp = outputStream.responseFor

    InvalidJson(resp)

    verify(resp).setStatus(ScInternalServerError)
    verify(resp).setContentType("application/json")

    outputStream.getLowStr must include ("invalid")
    outputStream.getLowStr must include  ("json")
  }

  test("Extensions return error on encoder failure") {
    val clientResp = mocks.EmptyResponse()
    val noneEncoder: Encoder = _ => None

    Extensions.values filter (_ != JsonExt) foreach { ext: Extension =>
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      ext(noneEncoder, clientResp)(resp)

      verify(resp).setStatus(ScInternalServerError)
      verify(resp).setContentType("application/json")

      outputStream.getLowStr must include ("invalid")
      outputStream.getLowStr must include  ("json")
    }
  }

  test("DefaultResponse includes CORS header") {
    val resp = mock[HttpServletResponse]

    DefaultResponse(resp)

    verify(resp).setStatus(ScOk)
    verify(resp).setHeader("Access-Control-Allow-Origin", "*")
  }

  test("Extensions include CORS header on success") {
    val clientResp = mocks.EmptyResponse()
    val emptyEncoder: Encoder = _ => Some(Array.empty)

    Extensions.values foreach { ext: Extension =>
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      ext(emptyEncoder, clientResp)(resp)

      verify(resp).setStatus(ScOk)
      verify(resp).setHeader("Access-Control-Allow-Origin", "*")
    }
  }

  test("PbfExt sends bytes from encoder") {
    val clientResp = mock[Response]

    forAll { bytes: Array[Byte] =>
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor
      val encoder: Encoder = r => Some(bytes)

      PbfExt(encoder, clientResp)(resp)

      verify(resp).setStatus(ScOk)
      verify(resp).setContentType("application/octet-stream")
      verify(resp).setHeader("Access-Control-Allow-Origin", "*")

      outputStream.getBytes must equal (bytes)
    }
  }

  test("B64PbfExt sends base 64 encoded bytes from encoder") {
    val clientResp = mock[Response]

    forAll { bytes: Array[Byte] =>
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor
      val encoder: Encoder = r => Some(bytes)

      B64PbfExt(encoder, clientResp)(resp)

      verify(resp).setStatus(ScOk)
      verify(resp).setContentType("text/plain")
      verify(resp).setHeader("Access-Control-Allow-Origin", "*")

      outputStream.getString must equal (Base64.encodeBase64String(bytes))
    }
  }

  test("JsonExt returns the provided json") {
    val someValidJson = JsonReader.fromString("""
      [
         {"location":{"type":"Point","coordinates":[-87.542665,41.646774]}}
        ,{"location":{"type":"Point","coordinates":[-87.547293,41.653993]}}
        ,{"location":{"type":"Point","coordinates":[-87.550642,41.65339]}}
        ,{"location":{"type":"Point","coordinates":[-87.544576,41.65541]}}
        ,{"location":{"type":"Point","coordinates":[-87.546998,41.655416]}}
        ,{"location":{"type":"Point","coordinates":[-87.547014,41.651423]}}
        ,{"location":{"type":"Point","coordinates":[-87.543788,41.648308]}}
        ,{"location":{"type":"Point","coordinates":[-87.547015,41.651025]}}
        ,{"location":{"type":"Point","coordinates":[-87.546062,41.653587]}}
        ,{"location":{"type":"Point","coordinates":[-87.547294,41.653941]}}
        ,{"location":{"type":"Point","coordinates":[-87.543644,41.64809]}}
        ,{"location":{"type":"Point","coordinates":[-87.544579,41.654559]}}
        ,{"location":{"type":"Point","coordinates":[-87.54235,41.645861]}}
        ,{"location":{"type":"Point","coordinates":[-87.543783,41.648091]}}
        ,{"location":{"type":"Point","coordinates":[-87.54337,41.648969]}}
        ,{"location":{"type":"Point","coordinates":[-87.5427,41.653781]}}
        ,{"location":{"type":"Point","coordinates":[-87.547014,41.651365]}}
        ,{"location":{"type":"Point","coordinates":[-87.549424,41.653594]}}
        ,{"location":{"type":"Point","coordinates":[-87.54458,41.652677]}}
        ,{"location":{"type":"Point","coordinates":[-87.544505,41.648313]}}
      ]""")

    val ignored: Encoder = r => None
    val outputStream = new mocks.ByteArrayServletOutputStream
    val resp = outputStream.responseFor
    val clientResp = mock[Response]
    when(clientResp.jValue(JsonP)).thenReturn(someValidJson)

    JsonExt(ignored, clientResp)(resp)

    verify(resp).setStatus(ScOk)
    verify(resp).setContentType("application/json; charset=UTF-8")
    verify(resp).setHeader("Access-Control-Allow-Origin", "*")

    JsonReader.fromString(outputStream.getString) must equal (someValidJson)
  }
}
