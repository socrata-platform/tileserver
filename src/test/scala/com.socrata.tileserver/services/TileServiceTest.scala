package com.socrata.tileserver
package services

import java.nio.charset.StandardCharsets.UTF_8
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse._
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
import org.scalatest.mock.MockitoSugar

import com.socrata.http.client.Response
import com.socrata.http.server.HttpRequest
import com.socrata.http.server.routing.TypedPathComponent
import com.socrata.thirdparty.geojson.GeoJson._
import com.socrata.thirdparty.geojson.{FeatureCollectionJson, FeatureJson, GeoJsonBase}

import util.{CartoRenderer, GeoProvider, QuadTile, RequestInfo, TileEncoder}

// scalastyle:off import.grouping, null
class TileServiceTest extends TestBase with UnusedSugar with MockitoSugar {
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
      val (k, v): (String, String) = known
      val upstream = mocks.HeaderResponse(Map(known, unknown))
      val client = mocks.StaticCuratedClient(upstream)
      val resp = new mocks.ByteArrayServletOutputStream().responseFor

      val info = mocks.PngInfo(ext)

      TileService(Unused, util.GeoProvider(client)).handleRequest(info)(resp)

      verify(resp).setStatus(SC_OK)
      verify(resp).setHeader("Access-Control-Allow-Origin", "*")
      verify(resp).setHeader(k, v)
    }
  }

  test("Features are encoded according to extension") {
    import gen.Extensions._
    import gen.Points._

    forAll { (pt: ValidPoint, ext: Extension, complete: Boolean) =>
      val upstream = mocks.SeqResponse(fJson(pt))
      val client = mocks.StaticCuratedClient(upstream)
      val expected = Set(feature(pt))
      val expectedJson = Seq(fJson(pt))
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      val info =
        if (complete) mocks.PngInfo(ext) else mocks.PngInfo(ext, None, None)
      val renderer =
        CartoRenderer(mocks.StaticHttpClient(expected.toString), Unused)

      TileService(renderer, util.GeoProvider(client)).
        handleRequest(info)(resp)

      if (ext != Png || complete) {
        verify(resp).setStatus(SC_OK)
      } else {
        verify(resp).setStatus(SC_BAD_REQUEST)
      }

      val enc = TileEncoder(expected)

      ext match {
        case Pbf =>
          outputStream.getBytes must includeSlice (enc.bytes)
        case BPbf =>
          outputStream.getString must include (enc.base64)
        case Json =>
          outputStream.getString must equal (upstream.toString)
        case Png =>
          if (complete) {
            outputStream.getString must equal (expected.toString)
          } else {
            outputStream.getString must include ('$' + "style")
            outputStream.getString must include ('$' + "overscan")
          }
        // ".txt" should be supported, but its output format is unspecified.
        case Txt => ()
      }
    }
  }

  test("Requests without X-Socrata-Host use Host") {
    import gen.Extensions._

    val upstream = mocks.SeqResponse(Seq.empty)
    val client = mocks.StaticCuratedClient(upstream)
    val provider = util.GeoProvider(client)

    val overscan: Int = Unused

    forAll { ext: Extension =>
      val params: Map[String, String] =
        if (ext == Png) {
          Map('$' + "style" -> Unused, '$' + "overscan" -> overscan.toString)
        } else {
          Map.empty
        }

      val req = new mocks.StaticRequest(params,
                                        Map("Host" -> "host.test-socrata.com"),
                                        false)

      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      val renderer = CartoRenderer(mocks.StaticHttpClient(""), Unused)

      TileService(Unused, provider).
        handleRequest(reqInfo(req, Unused, Unused, Unused, ext))(resp)

      verify(resp).setStatus(SC_OK)
    }
  }

  test("Unknown errors are handled") {
    import gen.Extensions._

    forAll { (message: String, ext: Extension) =>
      val upstream = mocks.ThrowsResponse(message)
      val client = mocks.StaticCuratedClient(upstream)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      TileService(Unused, GeoProvider(client)).
        handleRequest(reqInfo(Unused, Unused, Unused, Unused, ext))(resp)

      verify(resp).setStatus(SC_INTERNAL_SERVER_ERROR)

      outputStream.getLowStr must include ("unknown")
      outputStream.getLowStr must include ("error")
      outputStream.getString must include (encode(message))
    }
  }

  test("Handle request returns OK when underlying succeeds") {
    import gen.Extensions._
    import gen.Points._

    forAll { (pt: ValidPoint, ext: Extension) =>
      val upstream = mocks.SeqResponse(fJson(pt))
      val client = mocks.StaticCuratedClient(upstream)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      val info = mocks.PngInfo(ext)

      TileService(Unused, GeoProvider(client)).handleRequest(info)(resp)

      verify(resp).setStatus(SC_OK)

      if (ext == Json) {
        outputStream.getString must include (upstream.toString)
      }
    }
  }

  test("Handle request fails when rendering a `.png` without `$style`") {
    import gen.Extensions._

    val upstream = mocks.SeqResponse(Seq.empty)
    val client = mocks.StaticCuratedClient(upstream)
    val outputStream = new mocks.ByteArrayServletOutputStream
    val resp = outputStream.responseFor

    val info = mocks.PngInfo(Png, None, Some(Unused: Int))
    TileService(Unused, GeoProvider(client)).handleRequest(info)(resp)

    verify(resp).setStatus(SC_BAD_REQUEST)
  }

  test("Handle request fails when rendering a `.png` without `$overscan`") {
    import gen.Extensions._

    val upstream = mocks.SeqResponse(Seq.empty)
    val client = mocks.StaticCuratedClient(upstream)
    val outputStream = new mocks.ByteArrayServletOutputStream
    val resp = outputStream.responseFor
    val info = mocks.PngInfo(Png, Some(Unused: String), None)

    TileService(Unused, GeoProvider(client)).handleRequest(info)(resp)

    verify(resp).setStatus(SC_BAD_REQUEST)
  }

  test("Handle request fails when rendering a `.png` invalid `$overscan`") {
    import gen.Extensions._

    val upstream = mocks.SeqResponse(Seq.empty)
    val client = mocks.StaticCuratedClient(upstream)
    val outputStream = new mocks.ByteArrayServletOutputStream
    val resp = outputStream.responseFor
    val req = mocks.StaticRequest(Map('$' + "style" -> Unused.toString,
                                      '$' + "overscan" -> Unused.toString))

    TileService(Unused, GeoProvider(client)).handleRequest(reqInfo(req, ext=Png))(resp)

    verify(resp).setStatus(SC_BAD_REQUEST)
  }

  test("Handle request returns OK when underlying succeeds for single FeatureJson") {
    import gen.Extensions._
    import gen.Points._

    forAll { (pt: ValidPoint, ext: Extension) =>
      val expected = FeatureCollectionJson(Seq(fJson(pt)))
      val upstream = mocks.SeqResponse(fJson(pt))
      val client = mocks.StaticCuratedClient(upstream)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor
      val info = mocks.PngInfo(ext)

      TileService(Unused, GeoProvider(client)).handleRequest(info)(resp)

      verify(resp).setStatus(SC_OK)

      if (ext == Json) {
        val actual = JsonUtil.parseJson[GeoJsonBase](outputStream.getString) match {
          case Right(jVal) => jVal
          case _ => fail("Decoding Json Failed!")
        }

        actual must equal (expected)
      }
    }
  }

  test("Handle request returns 304 with no body when given 304.") {
    val upstream = mock[Response]
    when(upstream.resultCode).thenReturn(SC_NOT_MODIFIED)

    val client = mocks.StaticCuratedClient(upstream)
    val outputStream = new mocks.ByteArrayServletOutputStream
    val resp = outputStream.responseFor

    TileService(Unused, GeoProvider(client)).handleRequest(Unused)(resp)

    verify(resp).setStatus(SC_NOT_MODIFIED)

    outputStream.getString must have length (0)
  }

  test("Handle request echos known codes") {
    import gen.StatusCodes._

    forAll { (statusCode: KnownStatusCode, payload: String) =>
      val message = s"""{message: ${encode(payload)}}"""
      val upstream = mock[Response]
      when(upstream.resultCode).thenReturn(statusCode)
      when(upstream.inputStream(anyInt)).thenReturn(mocks.StringInputStream(message))

      val client = mocks.StaticCuratedClient(upstream)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      TileService(Unused, GeoProvider(client)).handleRequest(Unused)(resp)

      verify(resp).setStatus(statusCode)

      outputStream.getLowStr must include ("underlying")
      outputStream.getString must include (encode(payload))
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

      val client = mocks.StaticCuratedClient(upstream)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      TileService(Unused, GeoProvider(client)).handleRequest(Unused)(resp)

      verify(resp).setStatus(SC_INTERNAL_SERVER_ERROR)

      outputStream.getLowStr must include ("underlying")
      outputStream.getString must include (encode(payload))
      outputStream.getString must include (statusCode.toString)
    }
  }

  test("Handle request returns 'internal server error' if underlying throws") {
    import gen.Extensions._

    forAll { (message: String, ext: Extension) =>
      val client = mocks.StaticCuratedClient {
        () => throw new RuntimeException(message)
      }

      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      TileService(Unused, GeoProvider(client)).
        handleRequest(reqInfo(ext))(resp)

      verify(resp).setStatus(SC_INTERNAL_SERVER_ERROR)

      outputStream.getLowStr must include ("unknown error")
      outputStream.getString must include (encode(message))
    }
  }

  test("Get returns success when underlying succeeds") {
    import gen.Extensions._
    import gen.Points._

    forAll { (pt: ValidPoint, ext: Extension) =>
      val upstream = mocks.SeqResponse(fJson(pt))
      val client = mocks.StaticCuratedClient(upstream)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      val overscan: Int = Unused
      val params: Map[String, String] = Map('$' + "style" -> Unused,
                                            '$' + "overscan" -> overscan.toString)
      val req: HttpRequest =
        if (ext == Png) mocks.StaticRequest(params) else Unused

      TileService(Unused, GeoProvider(client)).
        service(Unused,
                Unused,
                Unused,
                Unused,
                TypedPathComponent(Unused, ext)).
        get(req)(resp)

      verify(resp).setStatus(SC_OK)

      if (ext == Json) {
        outputStream.getString must equal (upstream.toString)
      }
    }
  }

  test("Echoed response must include known status code, content-type, and payload") {
    import gen.StatusCodes._

    forAll { (statusCode: KnownStatusCode, payload: String) =>
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor
      val upstream = mocks.StringResponse(json"""{payload: $payload}""".toString,
                                          statusCode)

      TileService.echoResponse(upstream)(resp)

      verify(resp).setStatus(statusCode)
      verify(resp).setContentType("application/json; charset=UTF-8")

      outputStream.getLowStr must include ("underlying")
      outputStream.getString must include (statusCode.toString)
      outputStream.getString must include (encode(payload))
    }
  }

  test("Echoing response succeeds when upstream throws") {
    import gen.StatusCodes._

    forAll { (statusCode: KnownStatusCode, message: String) =>
      val upstream = mocks.ThrowsResponse(message, statusCode)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      TileService.echoResponse(upstream)(resp)

      verify(resp).setStatus(statusCode)
      verify(resp).setContentType("application/json; charset=UTF-8")

      outputStream.getLowStr must include ("underlying")
      outputStream.getLowStr must include ("failed")
      outputStream.getString must include (statusCode.toString)
      outputStream.getString must include (encode(message))
    }
  }

  test("If cause has a message, fatal errors must include it") {
    forAll { (message: String, causeMessage: String) =>
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor
      val cause = new NoStackTrace {
        override def getMessage: String = causeMessage
      }

      TileService.fatal(message, cause)(resp)

      verify(resp).setStatus(SC_INTERNAL_SERVER_ERROR)
      verify(resp).setContentType("application/json; charset=UTF-8")

      outputStream.getLowStr must include ("message")
      outputStream.getString must include (encode(message))
      outputStream.getLowStr must include ("cause")
      outputStream.getString must include (encode(causeMessage))
    }
  }

  test("If only the root cause has a message fatal errors must still include it") {
    forAll { (message: String, causeMessage: String) =>
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor
      val cause = new NoStackTrace {
        override def getMessage: String = causeMessage
      }

      TileService.fatal(message, new RuntimeException(null, cause))(resp)

      verify(resp).setStatus(SC_INTERNAL_SERVER_ERROR)
      verify(resp).setContentType("application/json; charset=UTF-8")

      outputStream.getLowStr must include ("message")
      outputStream.getString must include (encode(message))
      outputStream.getLowStr must include ("cause")
      outputStream.getString must include (encode(causeMessage))
    }
  }

  test("Fatal errors must include the provided message; even if the cause has no message") {
    forAll { (message: String) =>
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor
      val cause = new RuntimeException()

      TileService.fatal(message, new RuntimeException(null, cause))(resp)

      verify(resp).setStatus(SC_INTERNAL_SERVER_ERROR)
      verify(resp).setContentType("application/json; charset=UTF-8")

      outputStream.getLowStr must include ("message")
      outputStream.getString must include (encode(message))
    }
  }

  test("An empty message is successfully unpacked") {
    import gen.Extensions._

    forAll { (ext: Extension) =>
      val upstream = mocks.MsgPackResponse()
      val client = mocks.StaticCuratedClient(upstream)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor
      val info = mocks.PngInfo(ext)
      val renderer = CartoRenderer(mocks.StaticHttpClient(""), Unused)

      TileService(renderer, util.GeoProvider(client)).
        handleRequest(info)(resp)

      verify(resp).setStatus(SC_OK)

      if (ext != Json) {
        outputStream.getString must be ('empty)
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
      val client = mocks.StaticCuratedClient(upstream)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      TileService(Unused, util.GeoProvider(client)).
        handleRequest(reqInfo("pbf"))(resp)

      verify(resp).setStatus(SC_INTERNAL_SERVER_ERROR)

      outputStream.getLowStr must include ("invalid")
      outputStream.getLowStr must include ("underlying")
    }
  }

  test("Invalid headers are rejected when unpacking") {
    import gen.Extensions._

    val badMessage: Array[Byte] = Array(3, 2, 1, 0)

    forAll { (ext: Extension) =>
      val upstream = mocks.BinaryResponse(badMessage)
      val client = mocks.StaticCuratedClient(upstream)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      TileService(Unused, util.GeoProvider(client)).
        handleRequest(reqInfo(ext))(resp)

      verify(resp).setStatus(SC_INTERNAL_SERVER_ERROR)

      outputStream.getLowStr must include ("invalid")
      outputStream.getLowStr must include ("underlying")
    }
  }

  test("Message pack null is rejected when unpacking") {
    import gen.Extensions._

    val msgNull: Array[Byte] = Array(-64)

    forAll { ext: Extension =>
      val upstream = mocks.BinaryResponse(msgNull)
      val client = mocks.StaticCuratedClient(upstream)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      TileService(Unused, util.GeoProvider(client)).
        handleRequest(reqInfo(ext))(resp)

      verify(resp).setStatus(SC_INTERNAL_SERVER_ERROR)

      outputStream.getLowStr must include ("invalid")
      outputStream.getLowStr must include ("underlying")
    }
  }

  test("Invalid `geometry_index`s are rejected when unpacking") {
    import gen.Extensions._

    forAll { (idx: Int, ext: Extension) =>
      whenever (idx < 0) {
        val upstream = mocks.MsgPackResponse(-1)
        val client = mocks.StaticCuratedClient(upstream)
        val outputStream = new mocks.ByteArrayServletOutputStream
        val resp = outputStream.responseFor

        TileService(Unused, util.GeoProvider(client)).
          handleRequest(reqInfo(ext))(resp)

        verify(resp).setStatus(SC_INTERNAL_SERVER_ERROR)

        outputStream.getLowStr must include ("invalid")
        outputStream.getLowStr must include ("underlying")
      }
    }
  }

  test("`$style` and `$overscan` are not passed upstream") {
    import gen.Extensions._

    forAll { ext: Extension =>
      val upstream = mocks.StringResponse(Unused)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor
      val info = mocks.PngInfo(ext, Some(Unused: String), Some(Unused: Int))

      val client = mocks.StaticCuratedClient.withReq { request =>
        val actual = request(Unused).builder
        actual.query.toMap.get("$style") must be ('empty)

        val pt: (Int, Int) = (Unused, Unused)
        mocks.SeqResponse(fJson(pt))
      }

      TileService(Unused, GeoProvider(client)).
        handleRequest(info)(resp)

      verify(resp).setStatus(SC_OK)
    }
  }

  test("X-Socrata-RequestId is passed to CartoRenderer") {
    import gen.Extensions.Png

    forAll { requestId: String =>
      val http = mocks.DynamicHttpClient { req =>
        val headers = req.builder.headers.toMap
        headers.contains("X-Socrata-RequestId") must be (true)
        headers("X-Socrata-RequestId") must equal (requestId)

        mocks.EmptyResponse()
      }

      val renderer = CartoRenderer(http, Unused)

      val upstream = mocks.SeqResponse(fJson())
      val client = mocks.StaticCuratedClient(upstream)
      val req = mocks.StaticRequest("$style" -> (Unused: String),
                                    "X-Socrata-RequestId" -> requestId)

      TileService(renderer, GeoProvider(client)).
        handleRequest(reqInfo(req, Png)): Unit
    }
  }
}
