package com.socrata.tileserver
package util

import java.nio.charset.StandardCharsets.UTF_8

import com.rojoma.json.v3.codec.JsonEncode
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
        RenderProvider(client, Unused).renderPng(Unused, styleInfo, Unused), UTF_8)

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
        the [FailedRenderException] thrownBy renderer.renderPng(Unused, styleInfo, Unused)
      actual must equal (expected)
    }
  }

  test("renderPng throws on error") {
    import gen.SmallMapTiles._

    forAll { (tile: SmallMapTile, z: Int, css: String, message: String) =>
      val resp = mocks.ThrowsResponse(message)
      val client = testcommon.mocks.StaticHttpClient(resp)
      val renderer = RenderProvider(client, Unused)
      val info = new RequestInfo(Unused, Unused, Unused, Unused, Unused, None) {
        override val style = Some(css)
        override val zoom = z
      }

      val actual =
        the [Exception] thrownBy renderer.renderPng(Unused, info, info.style.get) // scalastyle:ignore
      actual.getMessage must equal (message)
    }
  }

  test("renderPng returns expected response") {
    import gen.SmallMapTiles._

    def makeResp(salt: String): (SimpleHttpRequest => Response) = { req =>
      val blob = IOUtils.toByteArray(req.asInstanceOf[BlobHttpRequest].contents)
      val unpacked = MsgPack.unpack(blob).asInstanceOf[Map[String, Any]]

      val tile = Base64.encodeBase64String(MsgPack.pack(unpacked("tile").asInstanceOf[MapTile])) // Whee!
      val z = unpacked("zoom")
      val css = unpacked("style")

      mocks.StringResponse(salt + tile + z + css)
    }

    forAll { (salt: String, tile: SmallMapTile, z: Int, css: String) =>
      val client = testcommon.mocks.StaticHttpClient(makeResp(salt))
      val renderer = RenderProvider(client, Unused)
      val info = new RequestInfo(Unused, Unused, Unused, Unused, Unused, None) {
        override val style = Some(css)
        override val zoom: Int = z
      }

      val suffix = z + css

      val serialized = IOUtils.toString(renderer.renderPng(tile, info, info.style.get))
      serialized.take(salt.length) must equal (salt)
      serialized.takeRight(suffix.length) must equal (suffix)

      val expected: MapTile = tile // Convert types.
      val b64encoded = serialized.stripPrefix(salt).stripSuffix(suffix)
      // Types?  Where we're going we won't need types!
      val actual = MsgPack.unpack(Base64.decodeBase64(b64encoded)).asInstanceOf[MapTile]

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
    renderer.renderPng(Unused, info, info.style.get): Unit
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
      renderer.renderPng(Unused, info, Unused): Unit
    }
  }
}
