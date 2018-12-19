package com.socrata.tileserver
package services

import java.nio.charset.StandardCharsets.UTF_8
import javax.servlet.http.HttpServletRequest

import scala.util.control.NoStackTrace
import scala.util.{Failure, Success}

import com.rojoma.json.v3.ast.{JValue, JObject, JString, JNull}
import com.rojoma.json.v3.util.JsonUtil
import com.rojoma.json.v3.interpolation._
import com.rojoma.simplearm.v2.ResourceScope
import com.socrata.http.server.util.RequestId.ReqIdHeader
import com.vividsolutions.jts.io.WKBWriter
import org.mockito.Matchers.{anyInt, anyString, anyObject, eq => matcher}
import org.mockito.Mockito.{verify, when}
import org.scalatest.mockito.MockitoSugar

import com.socrata.http.client.Response
import com.socrata.http.server.HttpRequest
import com.socrata.http.server.responses._
import com.socrata.http.server.routing.TypedPathComponent
import com.socrata.testcommon
import com.socrata.testcommon.ResponseSugar
import com.socrata.thirdparty.geojson.GeoJson._
import com.socrata.thirdparty.geojson.{FeatureCollectionJson, FeatureJson, GeoJsonBase}

import util.{RenderProvider, GeoProvider, QuadTile, RequestInfo, TileEncoder}

// scalastyle:off import.grouping, null
class TileServiceTest
    extends TestBase
    with UnusedSugar
    with MockitoSugar
    with ResponseSugar {
  test("Service supports at least .pbf, .bpbf, .json and .png") {
    val svc = TileService(Unused, GeoProvider(Unused))

    svc.types must contain ("pbf")
    svc.types must contain ("bpbf")
    svc.types must contain ("json")
    svc.types must contain ("png")
  }

  test("Correct headers are returned") {
    import gen.Extensions._
    import gen.Headers._

    forAll { (known: OutgoingHeader,
              unknown: UnknownHeader,
              ext: Extension) =>
      val upstream = mocks.HeaderResponse(Map(known, unknown))
      val client = testcommon.mocks.StaticCuratedClient(upstream)

      val info = mocks.PngInfo(ext)

      val resp = unpackResponse(
        TileService(Unused, util.GeoProvider(client)).handleRequest(info))

      resp.status must equal (OK.statusCode)
      resp.headers("Access-Control-Allow-Origin") must equal ("*")
      resp.headers(known.key) must equal (known.value)
    }
  }

  test("Features are encoded according to extension") {
    import gen.Extensions._
    import gen.Points._

    forAll { (pt: ValidPoint, ext: Extension, complete: Boolean) =>
      val upstream = mocks.SeqResponse(fJson(pt))
      val client = testcommon.mocks.StaticCuratedClient(upstream)
      val expected = Set(feature(pt))
      val expectedJson = Seq(fJson(pt))

      val info = mocks.PngInfo(ext, complete)
      val renderer =
        RenderProvider(testcommon.mocks.StaticHttpClient(expected.toString), Unused)

      val resp = unpackResponse(
        TileService(renderer, util.GeoProvider(client)).handleRequest(info))

      if (ext != Png || complete) {
        resp.status must equal (OK.statusCode)
      } else {
        resp.status must equal(BadRequest.statusCode)
      }

      val enc = TileEncoder(expected)

      ext match {
        case Pbf =>
          resp.body.toByteArray must includeSlice (enc.bytes)
        case BPbf =>
          resp.body.toString must include (enc.base64)
        case Json =>
          resp.body.toString must equal (upstream.toString)
        case Png =>
          if (complete) {
            resp.body.toString must equal (expected.toString)
          } else {
            resp.body.toString must include ('$' + "style")
          }
        // ".txt" should be supported, but its output format is unspecified.
        case Txt => ()
      }
    }
  }

  test("Correct headers are returned on OPTIONS call") {
    import gen.Extensions._

    forAll { (ext: Extension) =>
      val upstream = mocks.FeatureIteratorResponse(Iterator.empty)
      val client = testcommon.mocks.StaticCuratedClient(upstream)

      val overscan: Int = Unused
      val params: Map[String, String] = Map('$' + "style" -> Unused,
        '$' + "overscan" -> overscan.toString)
      val req: HttpRequest =
        if (ext == Png) mocks.StaticRequest(params) else Unused

      val resp = unpackResponse(TileService(Unused, GeoProvider(client)).
        service(Unused,
          Unused,
          Unused,
          Unused,
          TypedPathComponent(Unused, ext)).
        options(req))

      resp.status must equal (OK.statusCode)
      resp.headers("Access-Control-Allow-Origin") must equal ("*")
      resp.headers("Access-Control-Allow-Headers") must equal ("X-Socrata-Host, X-Socrata-RequestId")
    }
  }

  test("Requests without X-Socrata-Host use Host as X-Socrata-Host") {
    import gen.Extensions._

    val host = "host.test-socrata.com"
    val overscan: Int = Unused
    val upstream = mocks.FeatureIteratorResponse(Iterator.empty)

    val client = testcommon.mocks.StaticCuratedClient { curatedReq =>
      val req = curatedReq(Unused)
      val headers = req.builder.headers.map { x => x }.toMap
      headers("X-Socrata-Host") must equal (host)
      upstream
    }

    forAll { ext: Extension =>
      val geoProvider = mocks.StaticGeoProvider(client)(upstream)

      val params: Map[String, String] =
        if (ext == Png) {
          Map('$' + "style" -> Unused, '$' + "overscan" -> overscan.toString)
        } else {
          Map.empty
        }

      val req = testcommon.mocks.StaticRequest(Map("Host" -> host), params)
      val resp = unpackResponse(
        TileService(Unused, geoProvider).handleRequest(reqInfo(req, Unused, Unused, Unused, ext)))

      if (!geoProvider.exceptions.isEmpty) {
        throw geoProvider.exceptions.head
      }

      resp.status must equal (OK.statusCode)
    }
  }

  test("Unknown errors are handled") {
    import gen.Extensions._

    forAll { (message: String, ext: Extension) =>
      val upstream = mocks.ThrowsResponse(message)
      val client = testcommon.mocks.StaticCuratedClient(upstream)
      val info = reqInfo(Unused, Unused, Unused, Unused, ext)

      val resp = unpackResponse(
        TileService(Unused, GeoProvider(client)).handleRequest(info))

      resp.status must equal (InternalServerError.statusCode)
      resp.body.toLowStr must include ("unknown")
      resp.body.toLowStr must include ("error")
      resp.body.toString must include (encode(message))
    }
  }

  test("Handling request returns OK when underlying succeeds") {
    import gen.Extensions._
    import gen.Points._

    forAll { (pt: ValidPoint, ext: Extension) =>
      val upstream = mocks.SeqResponse(fJson(pt))
      val client = testcommon.mocks.StaticCuratedClient(upstream)

      val info = mocks.PngInfo(ext)

      val resp = unpackResponse(TileService(Unused, GeoProvider(client)).handleRequest(info))

      resp.status must equal (OK.statusCode)

      if (ext == Json) {
        resp.body.toString must include (upstream.toString)
      }
    }
  }

  test("Handling request fails when rendering a `.png` without `$style`") {
    import gen.Extensions._

    val upstream = mocks.SeqResponse(Seq.empty)
    val client = testcommon.mocks.StaticCuratedClient(upstream)

    val info = mocks.PngInfo(Png, None, Some(Unused: Int))
    val resp = unpackResponse(TileService(Unused, GeoProvider(client)).handleRequest(info))

    resp.status must equal (BadRequest.statusCode)
  }

  test("Handling request succeeds when rendering a `.png` without `$overscan`") {
    import gen.Extensions._

    val upstream = mocks.SeqResponse(Seq.empty)
    val client = testcommon.mocks.StaticCuratedClient(upstream)
    val info = mocks.PngInfo(Png, Some(Unused: String), None)

    val resp = unpackResponse(TileService(Unused, GeoProvider(client)).handleRequest(info))

    resp.status must equal (OK.statusCode)
  }

  test("Handling request succeeds when rendering a `.png` with invalid `$overscan`") {
    import gen.Extensions._

    val upstream = mocks.SeqResponse(Seq.empty)
    val client = testcommon.mocks.StaticCuratedClient(upstream)
    val req = mocks.StaticRequest(Map('$' + "style" -> Unused.toString,
                                      '$' + "overscan" -> "Invalid"))

    val resp = unpackResponse(
      TileService(Unused, GeoProvider(client)).handleRequest(reqInfo(req, ext=Png)))

    resp.status must equal (OK.statusCode)
  }

  test("Handling request returns OK when underlying succeeds for single FeatureJson") {
    import gen.Extensions._
    import gen.Points._

    forAll { (pt: ValidPoint, ext: Extension) =>
      val expected = FeatureCollectionJson(Seq(fJson(pt)))
      val upstream = mocks.SeqResponse(fJson(pt))
      val client = testcommon.mocks.StaticCuratedClient(upstream)
      val info = mocks.PngInfo(ext)

      val resp = unpackResponse(TileService(Unused, GeoProvider(client)).handleRequest(info))

      resp.status must equal (OK.statusCode)

      if (ext == Json) {
        val actual = JsonUtil.parseJson[GeoJsonBase](resp.body.toString) match {
          case Right(jVal) => jVal
          case _ => fail("Decoding Json Failed!")
        }

        actual must equal (expected)
      }
    }
  }

  test("Handling request returns 304 with no body when given 304.") {
    val upstream = mock[Response]
    when(upstream.resultCode).thenReturn(NotModified.statusCode)

    val client = testcommon.mocks.StaticCuratedClient(upstream)

    val resp = unpackResponse(TileService(Unused, GeoProvider(client)).handleRequest(Unused))

    resp.status must equal (NotModified.statusCode)
    resp.body.toString must have length (0)
  }

  test("Handling request echos known codes") {
    import gen.StatusCodes._

    forAll { (statusCode: KnownStatusCode, payload: String) =>
      val message = s"""{message: ${encode(payload)}}"""
      val upstream = mock[Response]
      when(upstream.resultCode).thenReturn(statusCode)
      when(upstream.inputStream(anyInt)).thenReturn(mocks.StringInputStream(message))

      val client = testcommon.mocks.StaticCuratedClient(upstream)

      val resp = unpackResponse(TileService(Unused, GeoProvider(client)).handleRequest(Unused))

      resp.status must equal (statusCode: Int)
      resp.body.toLowStr must include ("underlying")
      resp.body.toString must include (encode(payload))
    }
  }

  test("Handle request returns 'internal server error' on unknown status") {
    import gen.StatusCodes._

    forAll { (statusCode: UnknownStatusCode, payload: String) =>
      val message = s"""{message: ${encode(payload)}}"""
      val upstream = mock[Response]
      when(upstream.resultCode).thenReturn(statusCode)
      when(upstream.inputStream(anyInt)).
        thenReturn(mocks.StringInputStream(message))

      val client = testcommon.mocks.StaticCuratedClient(upstream)

      val resp = unpackResponse(TileService(Unused, GeoProvider(client)).handleRequest(Unused))

      resp.status must equal (InternalServerError.statusCode)
      resp.body.toLowStr must include ("underlying")
      resp.body.toString must include (encode(payload))
      resp.body.toString must include (statusCode.toInt.toString)
    }
  }

  test("Handle request returns 'internal server error' if underlying throws") {
    import gen.Extensions._

    forAll { (message: String, ext: Extension) =>
      val client = testcommon.mocks.StaticCuratedClient {
        () => throw new RuntimeException(message)
      }

      val resp = unpackResponse(TileService(Unused, GeoProvider(client)).
                                  handleRequest(reqInfo(ext)))

      resp.status must equal (InternalServerError.statusCode)
      resp.body.toLowStr must include ("unknown error")
      resp.body.toString must include (encode(message))
    }
  }

  test("Get returns success when underlying succeeds") {
    import gen.Extensions._
    import gen.Points._

    forAll { (pt: ValidPoint, ext: Extension) =>
      val upstream = mocks.SeqResponse(fJson(pt))
      val client = testcommon.mocks.StaticCuratedClient(upstream)

      val overscan: Int = Unused
      val params: Map[String, String] = Map('$' + "style" -> Unused,
                                            '$' + "overscan" -> overscan.toString)
      val req: HttpRequest =
        if (ext == Png) mocks.StaticRequest(params) else Unused

      val resp = unpackResponse(TileService(Unused, GeoProvider(client)).
                                  service(Unused,
                                          Unused,
                                          Unused,
                                          Unused,
                                          TypedPathComponent(Unused, ext)).
                                  get(req))

      resp.status must equal (OK.statusCode)

      if (ext == Json) {
        resp.body.toString must equal (upstream.toString)
      }
    }
  }

  test("Echoed response must include known status code, content-type, and payload") {
    import gen.StatusCodes._

    forAll { (statusCode: KnownStatusCode, payload: String) =>
      val upstream = mocks.StringResponse(json"""{payload: $payload}""".toString,
                                          statusCode)

      val resp = unpackResponse(TileService.echoResponse(upstream))

      resp.status must equal (statusCode: Int)
      resp.contentType must equal ("application/json; charset=UTF-8")
      resp.body.toLowStr must include ("underlying")
      resp.body.toString must include (statusCode.toInt.toString)
      resp.body.toString must include (encode(payload))
    }
  }

  test("Echoing response succeeds when upstream throws") {
    import gen.StatusCodes._

    forAll { (statusCode: KnownStatusCode, message: String) =>
      val upstream = mocks.ThrowsResponse(message, statusCode)

      val resp = unpackResponse(TileService.echoResponse(upstream))

      resp.status must equal (statusCode: Int)
      resp.contentType must equal ("application/json; charset=UTF-8")
      resp.body.toLowStr must include ("underlying")
      resp.body.toLowStr must include ("failed")
      resp.body.toString must include (statusCode.toInt.toString)
      resp.body.toString must include (encode(message))
    }
  }

  test("If cause has a message, fatal errors must include it") {
    forAll { (message: String, causeMessage: String) =>
      val cause = new NoStackTrace {
        override def getMessage: String = causeMessage
      }

      val resp = unpackResponse(TileService.fatal(message, cause))

      resp.status must equal (InternalServerError.statusCode)
      resp.contentType must equal ("application/json; charset=UTF-8")
      resp.body.toLowStr must include ("message")
      resp.body.toString must include (encode(message))
      resp.body.toLowStr must include ("cause")
      resp.body.toString must include (encode(causeMessage))
    }
  }

  test("If only the root cause has a message fatal errors must still include it") {
    forAll { (message: String, causeMessage: String) =>
      val cause = new NoStackTrace {
        override def getMessage: String = causeMessage
      }

      val resp = unpackResponse(TileService.fatal(message, new RuntimeException(null, cause)))

      resp.status must equal (InternalServerError.statusCode)
      resp.contentType must equal ("application/json; charset=UTF-8")
      resp.body.toLowStr must include ("message")
      resp.body.toString must include (encode(message))
      resp.body.toLowStr must include ("cause")
      resp.body.toString must include (encode(causeMessage))
    }
  }

  test("Fatal errors must include the provided message; even if the cause has no message") {
    forAll { (message: String) =>
      val cause = new RuntimeException()

      val resp = unpackResponse(TileService.fatal(message, new RuntimeException(null, cause)))

      resp.status must equal (InternalServerError.statusCode)
      resp.contentType must equal ("application/json; charset=UTF-8")
      resp.body.toLowStr must include ("message")
      resp.body.toString must include (encode(message))
    }
  }

  test("An empty message is successfully unpacked") {
    import gen.Extensions._

    forAll { (ext: Extension) =>
      val upstream = mocks.MsgPackResponse()
      val client = testcommon.mocks.StaticCuratedClient(upstream)
      val info = mocks.PngInfo(ext)
      val renderer = RenderProvider(testcommon.mocks.StaticHttpClient(), Unused)

      val resp = unpackResponse(
        TileService(renderer, util.GeoProvider(client)).handleRequest(info))

      resp.status must equal (OK.statusCode)

      if (ext != Json) {
        resp.body.toString must be ('empty)
      }
    }
  }

  test("Invalid WKB is handled correctly as parsing error") {
    import gen.Points._

    val writer = new WKBWriter()
    val expectedJson = JObject(Map(mocks.MsgPackResponse.GeoIndexKey -> JString("0")))
    val invalidWKB = Array[Byte](3, 2, 1, 0)

    forAll { pts: Seq[ValidPoint] =>
      val upstream = mocks.MsgPackResponse(pts, invalidWKB)
      val client = testcommon.mocks.StaticCuratedClient(upstream)

      val resp = unpackResponse(
        TileService(Unused, util.GeoProvider(client)).handleRequest(reqInfo("pbf")))

      resp.status must equal (InternalServerError.statusCode)
      resp.body.toLowStr must include ("invalid")
      resp.body.toLowStr must include ("underlying")
    }
  }

  test("Invalid headers are rejected when unpacking") {
    import gen.Extensions._

    val badMessage: Array[Byte] = Array(3, 2, 1, 0)

    forAll { (ext: Extension) =>
      val upstream = testcommon.mocks.StaticResponse(badMessage)
      val client = testcommon.mocks.StaticCuratedClient(upstream)

      val resp = unpackResponse(
        TileService(Unused, util.GeoProvider(client)).handleRequest(reqInfo(ext)))

      resp.status must equal (InternalServerError.statusCode)
      resp.body.toLowStr must include ("invalid")
      resp.body.toLowStr must include ("underlying")
    }
  }

  test("Message pack null is rejected when unpacking") {
    import gen.Extensions._

    val msgNull: Array[Byte] = Array(-64)

    forAll { ext: Extension =>
      val upstream = testcommon.mocks.StaticResponse(msgNull)
      val client = testcommon.mocks.StaticCuratedClient(upstream)

      val resp = unpackResponse(
        TileService(Unused, util.GeoProvider(client)).handleRequest(reqInfo(ext)))

      resp.status must equal (InternalServerError.statusCode)

      resp.body.toLowStr must include ("invalid")
      resp.body.toLowStr must include ("underlying")
    }
  }

  test("Invalid `geometry_index`s are rejected when unpacking") {
    import gen.Extensions._

    forAll { (idx: Int, ext: Extension) =>
      whenever (idx < 0) {
        val upstream = mocks.MsgPackResponse(-1)
        val client = testcommon.mocks.StaticCuratedClient(upstream)

        val resp = unpackResponse(
          TileService(Unused, util.GeoProvider(client)).handleRequest(reqInfo(ext)))

        resp.status must equal (InternalServerError.statusCode)
        resp.body.toLowStr must include ("invalid")
        resp.body.toLowStr must include ("underlying")
      }
    }
  }

  test("`$style` and `$overscan` are not passed upstream") {
    import gen.Extensions._

    forAll { ext: Extension =>
      val upstream = mocks.StringResponse(Unused)
      val info = mocks.PngInfo(ext, Some(Unused: String), Some(Unused: Int))

      val client = testcommon.mocks.StaticCuratedClient { request =>
        val actual = request(Unused).builder
        actual.query.toMap.get("$style") must be ('empty)

        val pt: (Int, Int) = (Unused, Unused)
        mocks.SeqResponse(fJson(pt))
      }

      val resp = unpackResponse(
        TileService(Unused, GeoProvider(client)).handleRequest(info))

      resp.status must equal (OK.statusCode)
    }
  }

  test("X-Socrata-RequestId is passed to RenderProvider") {
    import gen.Extensions.Png

    forAll { requestId: String =>
      val http = testcommon.mocks.StaticHttpClient { req =>
        val headers = req.builder.headers.toMap
        headers.contains("X-Socrata-RequestId") must be (true)
        headers("X-Socrata-RequestId") must equal (requestId)

        mocks.EmptyResponse()
      }

      val renderer = RenderProvider(http, Unused)

      val upstream = mocks.SeqResponse(fJson())
      val client = testcommon.mocks.StaticCuratedClient(upstream)
      val req = mocks.StaticRequest("$style" -> (Unused: String),
                                    "X-Socrata-RequestId" -> requestId)

      TileService(renderer, GeoProvider(client)).
        handleRequest(reqInfo(req, Png)): Unit
    }
  }
}
