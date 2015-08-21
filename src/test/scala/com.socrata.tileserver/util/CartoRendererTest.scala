package com.socrata.tileserver
package util

import java.nio.charset.StandardCharsets.UTF_8
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}

import com.rojoma.json.v3.ast._
import com.rojoma.json.v3.io.JsonReader
import com.rojoma.simplearm.v2.ResourceScope
import org.apache.commons.io.IOUtils

import com.socrata.http.client.{exceptions => _, _}

import exceptions.FailedRenderException

// scalastyle:off import.grouping, no.whitespace.before.left.bracket
class CartoRendererTest extends TestBase with UnusedSugar {
  implicit val rs: ResourceScope = Unused

  test("handleResponse unpacks payload") {
    forAll { payload: String =>
      val expected = payload

      val resp = mocks.StringResponse(payload)
      val client = mocks.StaticHttpClient(resp)
      val actual = IOUtils.toString(CartoRenderer.handleResponse(resp), UTF_8)

      actual must equal (expected)
    }
  }

  test("handleResponse fails on non-200 responses") {
    import gen.StatusCodes._

    forAll { (payload: String, sc: NotOkStatusCode) =>
      val expected = FailedRenderException(payload)

      val resp = mocks.StringResponse(payload, sc)
      val actual =
        the [FailedRenderException] thrownBy CartoRenderer.handleResponse(resp)
      actual must equal (expected)
    }
  }

  test("renderPng throws on error") {
    forAll { (pbf: String, zoom: Int, css: String, message: String) =>
      val resp = mocks.ThrowsResponse(message)
      val client = mocks.StaticHttpClient(resp)
      val renderer = CartoRenderer(client, Unused)

      val actual =
        the [Exception] thrownBy renderer.renderPng(pbf, zoom, css, Unused) // scalastyle:ignore
      actual.getMessage must equal (message)
    }
  }

  def extractField(key: String, jVal: JValue): String = {
    val jObj = jVal.cast[JObject].get

    jObj.get(key) match {
      case Some(JString(str)) => str
      case Some(n: JNumber) => n.toString
      case _ => fail(s"Could not extract ${key}")
    }
  }

  test("renderPng returns expected response") {
    def makeResp(salt: String): (SimpleHttpRequest => Response) = req => {
      val jVal = JsonReader.fromEvents(req.asInstanceOf[JsonHttpRequest].contents)

      val pbf = extractField("bpbf", jVal)
      val zoom = extractField("zoom", jVal)
      val css = extractField("style", jVal)

      mocks.StringResponse(salt + pbf + zoom + css)
    }

    forAll { (salt: String, pbf: String, zoom: Int, css: String) =>
      val payload = salt + pbf + zoom + css

      val client = mocks.DynamicHttpClient(makeResp(salt))
      val renderer = CartoRenderer(client, Unused)

      val expected = payload.getBytes(UTF_8)
      val actual = IOUtils.toByteArray(renderer.renderPng(pbf, zoom, css, Unused))

      actual must equal (expected)
    }
  }

  test("renderPng passes x-socrata-requestid to renderer") {
    def requireRequestId(reqId: String): (SimpleHttpRequest => Response) = req => {
      val headers = req.builder.headers.map { pair =>
        val (k, v) = pair
        k.toLowerCase -> v
      }.toMap

      headers("x-socrata-requestid") must equal (reqId)
      mocks.StringResponse(Unused)
    }

    forAll { requestId: String =>
      val client = mocks.DynamicHttpClient(requireRequestId(requestId))

      val renderer = CartoRenderer(client, Unused)
      renderer.renderPng(Unused, Unused, Unused, requestId): Unit
    }
  }
}
