package com.socrata.tileserver
package handlers

import javax.servlet.http.HttpServletResponse._

import org.mockito.Mockito.{verify, when}
import org.scalatest.mock.MockitoSugar

import com.socrata.http.client.RequestBuilder
import com.socrata.http.server.responses._
import com.socrata.test.mocks

import exceptions.FailedRenderException
import util.{RenderProvider, RequestInfo}

class PngHandlerTest extends TestBase with UnusedSugar {
  test("Internal server error is returned when renderer fails to render") {
    val upstream = com.socrata.tileserver.mocks.ThrowsResponse(FailedRenderException(Unused))
    val client = com.socrata.tileserver.mocks.StaticHttpClient(upstream)
    val renderer = RenderProvider(client, RequestBuilder(Unused))
    val handler = PngHandler(renderer)
    val info = new RequestInfo(Unused, Unused, Unused, Unused, "png") {
      override val style = Some(Unused: String)
    }

    val resp = unpackResponse(
      handler(info)(OK, util.GeoResponse(com.socrata.tileserver.mocks.MsgPackResponse(), Unused)))

    resp.status must equal (SC_INTERNAL_SERVER_ERROR)
    resp.body.toLowStr must include ("failed to render")
  }
}
