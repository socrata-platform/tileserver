package com.socrata.tileserver
package handlers

import javax.servlet.http.HttpServletResponse._

import org.mockito.Mockito.{verify, when}
import org.scalatest.mock.MockitoSugar

import com.socrata.http.client.RequestBuilder
import com.socrata.http.server.responses._

import exceptions.FailedRenderException
import util.{CartoRenderer, RequestInfo}

class PngHandlerTest extends TestBase with UnusedSugar {
  test("500 is returned when renderer fails to render") {
    val upstream = mocks.ThrowsResponse(FailedRenderException(Unused))
    val client = mocks.StaticHttpClient(upstream)
    val outputStream = new mocks.ByteArrayServletOutputStream
    val resp = outputStream.responseFor
    val renderer = CartoRenderer(client, RequestBuilder(Unused))
    val handler = PngHandler(renderer)
    val info = new RequestInfo(Unused, Unused, Unused, Unused, "png") {
      override val style = Some(Unused: String)
    }

    handler(info)(OK, util.GeoResponse(mocks.MsgPackResponse(), Unused))(resp)

    verify(resp).setStatus(SC_BAD_REQUEST)
    outputStream.getString must include ("$style")
  }
}
