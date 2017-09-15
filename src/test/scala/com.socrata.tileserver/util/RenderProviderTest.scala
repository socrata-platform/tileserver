package com.socrata.tileserver
package util

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

import com.rojoma.json.v3.io.JsonReader
import com.rojoma.simplearm.v2.ResourceScope
import org.apache.commons.io.IOUtils
import org.velvia.MsgPack

import com.socrata.http.client.{exceptions => _, _}
import com.socrata.testcommon

import RenderProvider.MapTile
import exceptions.FailedRenderException

// scalastyle:off import.grouping, no.whitespace.before.left.bracket
class RenderProviderTest extends TestBase with UnusedSugar {
  val styleInfo = new RequestInfo(Unused, Unused, Unused, Unused, Unused) {
    override val style = Some(Unused: String)
  }

  test("handleResponse unpacks payload") {
    forAll { payload: String =>
      val expected = payload
      val resp = mocks.StringResponse(payload)
      val client = testcommon.mocks.StaticHttpClient(resp)
      val actual = IOUtils.toString(
        RenderProvider(client, Unused).renderPng(Unused, styleInfo), UTF_8)

      actual must equal (expected)
    }
  }

  test("handleResponse fails on non-200 responses") {
    import gen.StatusCodes._

    forAll { (payload: String, sc: NotOkStatusCode) =>
      val expected = FailedRenderException(payload)

      val resp = mocks.StringResponse(payload, sc)
      val client = testcommon.mocks.StaticHttpClient(resp)
      val renderer = RenderProvider(client, Unused)
      val actual =
        the [FailedRenderException] thrownBy renderer.renderPng(Unused, styleInfo)
      actual must equal (expected)
    }
  }

  test("renderPng throws on error") {
    forAll { (tile: MapTile, z: Int, css: String, message: String) =>
      val resp = mocks.ThrowsResponse(message)
      val client = testcommon.mocks.StaticHttpClient(resp)
      val renderer = RenderProvider(client, Unused)

      val info = new RequestInfo(Unused, Unused, Unused, Unused, Unused) {
        override val style = Some(css)
        override val zoom = z
      }

      val actual =
        the [Exception] thrownBy renderer.renderPng(tile, info) // scalastyle:ignore
      actual.getMessage must equal (message)
    }
  }

  test("renderPng passes x-socrata-federation to renderer") {
    def requireFederationHeader(): (SimpleHttpRequest => Response) = { req =>
      val headers = req.builder.headers.map { pair =>
        val (k, v) = pair
        k.toLowerCase -> v
      }.toMap

      headers("x-socrata-federation") must equal ("Honey Badger")
      mocks.StringResponse(Unused)
    }

    val client = testcommon.mocks.StaticHttpClient()
    val info = new RequestInfo(Unused, Unused, Unused, Unused, Unused) {
      override val style = Some(Unused: String)
    }

    val renderer = RenderProvider(client, Unused)
    renderer.renderPng(Unused, info): Unit
  }

  test("renderPng passes x-socrata-requestid to renderer") {
    def requireRequestId(reqId: String): (SimpleHttpRequest => Response) = { req =>
      val headers = req.builder.headers.map { pair =>
        val (k, v) = pair
        k.toLowerCase -> v
      }.toMap

      headers("x-socrata-requestid") must equal (reqId)
      mocks.StringResponse(Unused)
    }

    forAll { reqId: String =>
      val client = testcommon.mocks.StaticHttpClient(requireRequestId(reqId))
      val info = new RequestInfo(Unused, Unused, Unused, Unused, Unused) {
        override val style = Some(Unused: String)
        override val requestId = reqId
      }

      val renderer = RenderProvider(client, Unused)
      renderer.renderPng(Unused, info): Unit
    }
  }
}
