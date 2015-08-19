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

import com.socrata.http.client.{RequestBuilder, Response}
import com.socrata.http.server.HttpRequest
import com.socrata.http.server.HttpRequest.AugmentedHttpServletRequest
import com.socrata.http.server.routing.TypedPathComponent
import com.socrata.thirdparty.geojson.GeoJson._
import com.socrata.thirdparty.geojson.{FeatureCollectionJson, FeatureJson, GeoJsonBase}

import util.{CartoRenderer, GeoProvider, TileEncoder}

// scalastyle:off import.grouping
class TileServiceTest extends TestBase with UnusedSugar with MockitoSugar {
  test("Service supports at least .pbf, .bpbf, .json and .png") {
    val svc = TileService(Unused, GeoProvider(Unused))

    svc.types must contain ("pbf")
    svc.types must contain ("bpbf")
    svc.types must contain ("json")
    svc.types must contain ("png")
  }

  test("Correct returned headers are returned") {
    import gen.Extensions._
    import gen.Headers._

    forAll { (known: OutgoingHeader,
              unknown: UnknownHeader,
              ext: Extension) =>
      val (k, v): (String, String) = known
      val upstream = mocks.HeaderResponse(Map(known, unknown))
      val client = mocks.StaticCuratedClient(upstream)
      val resp = new mocks.ByteArrayServletOutputStream().responseFor

      val style: Map[String, String] =
        if (ext == Png) Map("$style" -> Unused) else Map.empty
      val req = mocks.StaticRequest(style)

      TileService(Unused, util.GeoProvider(client)).
        handleRequest(req, Unused, Unused, Unused, ext)(resp)

      verify(resp).setStatus(SC_OK)
      verify(resp).setHeader("Access-Control-Allow-Origin", "*")
      verify(resp).setHeader(k, v)
    }
  }

  test("Features are encoded according to extension") {
    import gen.Extensions._
    import gen.Points._

    forAll { (pt: ValidPoint, ext: Extension) =>
      val upstream = mocks.SeqResponse(fJson(pt))
      val client = mocks.StaticCuratedClient(upstream)
      val expected = Set(feature(pt))
      val expectedJson = Seq(fJson(pt))
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      val style: Map[String, String] =
        if (ext == Png) Map("$style" -> Unused) else Map.empty
      val req = mocks.StaticRequest(style)

      val renderer = CartoRenderer(mocks.StaticHttpClient(expected.toString),
                                   Unused)

      TileService(renderer, util.GeoProvider(client)).
        handleRequest(req, Unused, Unused, Unused, ext)(resp)

      verify(resp).setStatus(SC_OK)

      val enc = TileEncoder(expected, expectedJson)

      ext match {
        case Pbf =>
          outputStream.getBytes must includeSlice (enc.bytes)
        case BPbf =>
          outputStream.getString must include (enc.base64)
        case Json =>
          outputStream.getString must equal (upstream.toString)
        case Png =>
          outputStream.getString must equal (expected.toString)
        // ".txt" should be supported, but its output format is unspecified.
        case Txt => ()
      }
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
        handleRequest(Unused, Unused, Unused, Unused, ext)(resp)

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

      val req: HttpRequest =
        if (ext == Png) mocks.StaticRequest("$style" -> Unused) else Unused

      TileService(Unused, GeoProvider(client)).
        handleRequest(req, Unused, Unused, Unused, ext)(resp)

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

    TileService(Unused, GeoProvider(client)).
      handleRequest(Unused, Unused, Unused, Unused, Png)(resp)

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

      val req: HttpRequest =
        if (ext == Png) mocks.StaticRequest("$style" -> Unused) else Unused

      TileService(Unused, GeoProvider(client)).
        handleRequest(req, Unused, Unused, Unused, ext)(resp)

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

    TileService(Unused, GeoProvider(client)).
      handleRequest(Unused, Unused, Unused, Unused, Unused)(resp)

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

      TileService(Unused, GeoProvider(client)).
        handleRequest(Unused, Unused, Unused, Unused, Unused)(resp)

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

      TileService(Unused, GeoProvider(client)).
        handleRequest(Unused, Unused, Unused, Unused, Unused)(resp)

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
        handleRequest(Unused, Unused, Unused, Unused, ext)(resp)

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

      val req: HttpRequest =
        if (ext == Png) mocks.StaticRequest("$style" -> Unused) else Unused

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

  test("Fatal errors must include message and cause") {
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

  test("Correct request id is extracted") {
    forAll { (reqId: String) =>
      val req = mock[HttpRequest]
      val servReq = mock[HttpServletRequest]
      when(req.servletRequest).thenReturn(new AugmentedHttpServletRequest(servReq))
      when(servReq.getHeader(ReqIdHeader)).thenReturn(reqId)

      TileService.extractRequestId(req) must equal (reqId)
    }
  }

  test("Augmenting parameters adds to where and select") {
    import gen.Alphanumerics._

    val otherKey = "$other"
    val whereKey = "$where"
    val selectKey = "$select"

    forAll {(rawOtherValue: Alphanumeric,
             whereParam: (Alphanumeric, Alphanumeric),
             selectParam: (Alphanumeric, Alphanumeric)) =>
      val otherValue: String = rawOtherValue
      val (whereBase, whereValue) = whereParam: (String, String)
      val (selectBase, selectValue) = selectParam: (String, String)

      val neither = mocks.StaticRequest(otherKey -> otherValue)
      val where = mocks.StaticRequest(whereKey -> whereBase)
      val select = mocks.StaticRequest(selectKey -> selectBase)

      neither.queryParameters must have size (1)
      val nParams = TileService.augmentParams(neither,
                                              whereValue,
                                              selectValue)
      nParams must have size (3)
      nParams(otherKey) must equal (otherValue)
      nParams(whereKey) must equal (whereValue)
      nParams(selectKey) must equal (selectValue)

      val wParams = TileService.augmentParams(neither ++ where,
                                              whereValue,
                                              selectValue)
      wParams must have size (3)
      wParams(otherKey) must equal (otherValue)

      wParams(whereKey) must startWith (whereBase)
      wParams(whereKey) must endWith (whereValue)
      wParams(whereKey) must include regex ("\\s+and\\s+")

      wParams(selectKey) must equal (selectValue)

      val sParams = TileService.augmentParams(neither ++ select,
                                              whereValue,
                                              selectValue)
      sParams must have size (3)
      sParams(otherKey) must equal (otherValue)
      sParams(whereKey) must equal (whereValue)

      sParams(selectKey) must startWith (selectBase)
      sParams(selectKey) must endWith (selectValue)
      sParams(selectKey) must include regex (",\\s*")

      val wsParams = TileService.augmentParams(neither ++ where ++ select,
                                               whereValue,
                                               selectValue)
      sParams must have size (3)
      sParams(otherKey) must equal (otherValue)

      wParams(whereKey) must startWith (whereBase)
      wParams(whereKey) must endWith (whereValue)
      wParams(whereKey) must include regex ("\\s+and\\s+")

      sParams(selectKey) must startWith (selectBase)
      sParams(selectKey) must endWith (selectValue)
      sParams(selectKey) must include regex (",\\s*")
    }
  }

  test("An empty message is successfully unpacked") {
    import gen.Extensions._

    forAll { (ext: Extension) =>
      val upstream = mocks.MsgPackResponse()
      val client = mocks.StaticCuratedClient(upstream)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      val style: Map[String, String] =
        if (ext == Png) Map("$style" -> Unused) else Map.empty
      val req = mocks.StaticRequest(style)

      val renderer = CartoRenderer(mocks.StaticHttpClient(""), Unused)

      TileService(renderer, util.GeoProvider(client)).
        handleRequest(req, Unused, Unused, Unused, ext)(resp)

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
        handleRequest(Unused, Unused, Unused, Unused, "pbf")(resp)

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
        handleRequest(Unused, Unused, Unused, Unused, ext)(resp)

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
        handleRequest(Unused, Unused, Unused, Unused, ext)(resp)

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
          handleRequest(Unused, Unused, Unused, Unused, ext)(resp)

        verify(resp).setStatus(SC_INTERNAL_SERVER_ERROR)

        outputStream.getLowStr must include ("invalid")
        outputStream.getLowStr must include ("underlying")
      }
    }
  }

  test("`$style` is not passed upstream") {
    import gen.Extensions._

    forAll { ext: Extension =>
      val upstream = mocks.StringResponse(Unused)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor
      val req: HttpRequest = mocks.StaticRequest("$style" -> Unused)
      val client = mocks.StaticCuratedClient.withReq { request =>
        val actual = request(Unused).builder
        actual.query.toMap.get("$style") must be ('empty)

        val pt: (Int, Int) = (Unused, Unused)
        mocks.SeqResponse(fJson(pt))
      }

      TileService(Unused, GeoProvider(client)).
        handleRequest(req, Unused, Unused, Unused, ext)(resp)

      verify(resp).setStatus(SC_OK)
    }
  }

  test("X-Socrata-RequestId is passed to CartoRenderer") {
    import gen.Extensions.Png
    import gen.Points._

    forAll { requestId: String =>
      val renderer = mock[CartoRenderer]
      val upstream = mocks.SeqResponse(fJson())
      val client = mocks.StaticCuratedClient(upstream)
      val req = mocks.StaticRequest("$style" -> (Unused: String),
                                    "X-Socrata-RequestId" -> requestId)

      TileService(renderer, GeoProvider(client)).
        handleRequest(req, Unused, Unused, Unused, Png)

      verify(renderer).
        renderPng(anyString, anyInt, anyString, matcher(requestId))(anyObject[ResourceScope]): Unit
    }
  }
}
