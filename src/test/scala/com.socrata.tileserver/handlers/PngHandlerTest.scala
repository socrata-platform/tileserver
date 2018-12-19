package com.socrata.tileserver
package handlers

import org.mockito.Mockito.{verify, when}
import org.scalatest.mockito.MockitoSugar

import com.socrata.http.client.RequestBuilder
import com.socrata.http.server.responses._
import com.socrata.testcommon

import exceptions.FailedRenderException
import util.{RenderProvider, RequestInfo}

class PngHandlerTest extends TestBase with UnusedSugar {
  test("Internal server error is returned when renderer fails to render") {
    val upstream = mocks.ThrowsResponse(FailedRenderException(Unused))
    val client = testcommon.mocks.StaticHttpClient(upstream)
    val renderer = RenderProvider(client, RequestBuilder(Unused))
    val handler = PngHandler(renderer)
    val info = new RequestInfo(Unused, Unused, Unused, Unused, "png") {
      override val style = Some(Unused: String)
    }

    val resp = unpackResponse(
      handler(info)(OK, util.GeoResponse(mocks.MsgPackResponse(), Unused)))

    resp.status must equal (InternalServerError.statusCode)
    resp.body.toLowStr must include ("failed to render")
  }
}
