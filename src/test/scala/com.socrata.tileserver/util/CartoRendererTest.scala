package com.socrata.tileserver
package util

import java.nio.charset.StandardCharsets.UTF_8
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}
import scala.util.{Failure, Success}

import com.rojoma.json.v3.ast._
import com.rojoma.json.v3.io.JsonReader
import com.rojoma.simplearm.v2.ResourceScope
import org.apache.commons.io.IOUtils

import com.socrata.http.client.{exceptions => _, _}

import exceptions.FailedRenderException

// scalastyle:off import.grouping
class CartoRendererTest extends TestBase with UnusedSugar {
  implicit val rs: ResourceScope = Unused

  test("handleResponse unpacks payload") {
    forAll { payload: String =>
      val expected = Success(payload)

      val resp = mocks.StringResponse(payload)
      val client = mocks.StaticHttpClient(resp)
      val actual = CartoRenderer.handleResponse(Success(resp)).map {
        IOUtils.toString(_, UTF_8)
      }

      actual must equal (expected)
    }
  }

  test("handleResponse fails on non-200 responses") {
    import gen.StatusCodes._

    forAll { (payload: String, sc: NotOkStatusCode) =>
      val expected = Failure(FailedRenderException(payload))

      val resp = mocks.StringResponse(payload, sc)
      val actual = CartoRenderer.handleResponse(Success(resp))
      actual must equal (expected)
    }
  }

  test("mapnikXml does not throw") {
    forAll { (css: String, message: String) =>
      val resp = mocks.ThrowsResponse(message)
      val client = mocks.StaticHttpClient(resp)
      val renderer = CartoRenderer(client, Unused)

      val actual = renderer.mapnikXml(css)

      actual must be ('failure)
      actual.failed.get.getMessage must equal (message)
    }
  }

  test("renderPng does not throw") {
    forAll { (pbf: String, zoom: Int, css: String, message: String) =>
      val resp = mocks.ThrowsResponse(message)
      val client = mocks.StaticHttpClient(resp)
      val renderer = CartoRenderer(client, Unused)

      val actual = renderer.renderPng(pbf, zoom, css)

      actual must be ('failure)
      actual.failed.get.getMessage must equal (message)
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

  test("mapnikXml returns expected response") {
    def f(salt: String): (SimpleHttpRequest => Response) = req => {
      val jVal = JsonReader.fromEvents(req.asInstanceOf[JsonHttpRequest].contents)
      val css = extractField("style", jVal)

      mocks.StringResponse(salt + css)
    }

    forAll { (salt: String, css: String) =>
      val payload = salt + css

      val client = mocks.DynamicHttpClient(f(salt))
      val renderer = CartoRenderer(client, Unused)

      val expected = Success(payload)
      val actual = renderer.mapnikXml(css)

      actual must equal (expected)
    }
  }

  test("renderPng returns expected response") {
    def f(salt: String): (SimpleHttpRequest => Response) = req => {
      val jVal = JsonReader.fromEvents(req.asInstanceOf[JsonHttpRequest].contents)

      val pbf = extractField("bpbf", jVal)
      val zoom = extractField("zoom", jVal)
      val css = extractField("style", jVal)

      mocks.StringResponse(salt + pbf + zoom + css)
    }

    forAll { (salt: String, pbf: String, zoom: Int, css: String) =>
      val payload = salt + pbf + zoom + css

      val client = mocks.DynamicHttpClient(f(salt))
      val renderer = CartoRenderer(client, Unused)

      val expected = Success(payload.getBytes(UTF_8).toSeq)
      val actual = renderer.renderPng(pbf, zoom, css)

      actual.map(_.toSeq) must equal (expected)
    }
  }
}
