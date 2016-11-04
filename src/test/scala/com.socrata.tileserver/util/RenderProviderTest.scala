package com.socrata.tileserver
package util

import java.nio.charset.StandardCharsets.UTF_8

import com.rojoma.json.v3.io.JsonReader
import com.rojoma.simplearm.v2.ResourceScope
import com.socrata.tileserver.exceptions.FailedRenderException
import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.IOUtils
import org.velvia.MsgPack

import com.socrata.http.client.{exceptions => _, _}
import com.socrata.testcommon

import RenderProvider.MapTile
import exceptions.FailedRenderException

// scalastyle:off import.grouping, no.whitespace.before.left.bracket
class RenderProviderTest extends TestBase with UnusedSugar {
  val styleInfo = new RequestInfo(Unused, Unused, Unused, Unused, Unused, None) {
    override val style = Some(Unused: String)
  }

  val tileEncoder = new TileEncoder(Unused)

  test("handleResponse unpacks payload") {
    forAll { payload: String =>
      val expected = payload
      val resp = mocks.StringResponse(payload)
      val client = testcommon.mocks.StaticHttpClient(resp)
      val actual = IOUtils.toString(
        RenderProvider(client, Unused).renderPng(tileEncoder, styleInfo), UTF_8)

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
        the [FailedRenderException] thrownBy renderer.renderPng(tileEncoder, styleInfo)
      actual must equal (expected)
    }
  }

  test("renderPng throws on error") {
    forAll { (tile: MapTile, z: Int, css: String, message: String) =>
      val resp = mocks.ThrowsResponse(message)
      val client = testcommon.mocks.StaticHttpClient(resp)
      val renderer = RenderProvider(client, Unused)
      val info = new RequestInfo(Unused, Unused, Unused, Unused, Unused, None) {
        override val style = Some(css)
        override val zoom = z
      }

      val actual =
        the [Exception] thrownBy renderer.renderPng(tileEncoder, info) // scalastyle:ignore
      actual.getMessage must equal (message)
    }
  }

  test("renderPng returns expected response") {
    def makeResp(salt: String): (SimpleHttpRequest => Response) = { req =>
      val blob = IOUtils.toByteArray(req.asInstanceOf[BlobHttpRequest].contents)
      val unpacked = MsgPack.unpack(blob).asInstanceOf[Map[String, Any]]

      val tile =
        unpacked("tile").asInstanceOf[Map[String, Any]].map(_.toString).toSeq.sorted
      val z = unpacked("zoom")
      val css = unpacked("style")

      mocks.StringResponse(salt + tile + z + css)
    }

    forAll { (salt: String, rawTile: MapTile, z: Int, css: String) =>
      val tile = rawTile.mapValues(wkbs => wkbs.map(ft => ft map (x => Base64.encodeBase64String(x._1.getBytes())))).
          map(_.toString).toSeq.sorted

      val client = testcommon.mocks.StaticHttpClient(makeResp(salt))
      val renderer = RenderProvider(client, Unused)
      val info = new RequestInfo(Unused, Unused, Unused, Unused, Unused, None) {
        override val style = Some(css)
        override val zoom: Int = z
      }

      val expected = salt + tile + z + css
      val actual = IOUtils.toString(renderer.renderPng(tileEncoder, info))

      actual must equal (expected)
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
    val info = new RequestInfo(Unused, Unused, Unused, Unused, Unused, None) {
      override val style = Some(Unused: String)
    }

    val renderer = RenderProvider(client, Unused)
    renderer.renderPng(tileEncoder, info): Unit
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
      val info = new RequestInfo(Unused, Unused, Unused, Unused, Unused, None) {
        override val style = Some(Unused: String)
        override val requestId = reqId
      }

      val renderer = RenderProvider(client, Unused)
      renderer.renderPng(tileEncoder, info): Unit
    }
  }
}
