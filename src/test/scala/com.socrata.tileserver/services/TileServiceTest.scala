package com.socrata.tileserver
package services

import java.nio.charset.StandardCharsets.UTF_8
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse.{SC_INTERNAL_SERVER_ERROR => ScInternalServerError}
import javax.servlet.http.HttpServletResponse.{SC_NOT_MODIFIED => ScNotModified}
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}
import scala.util.control.NoStackTrace

import com.rojoma.json.v3.interpolation._
import com.socrata.http.server.util.RequestId.{RequestId, ReqIdHeader}
import org.mockito.Matchers.anyInt
import org.mockito.Mockito.{verify, when}
import org.scalatest.mock.MockitoSugar

import com.socrata.http.client.{RequestBuilder, Response}
import com.socrata.http.server.HttpRequest.AugmentedHttpServletRequest
import com.socrata.http.server.routing.TypedPathComponent
import com.socrata.http.server.HttpRequest

import util.TileEncoder

class TileServiceTest extends TestBase with UnusedSugar with MockitoSugar {
  test("Service supports at least .pbf, .bpbf and .json") {
    val svc = TileService(Unused)

    svc.types must contain ("pbf")
    svc.types must contain ("bpbf")
    svc.types must contain ("json")
  }

  test("Headers and parameters are correct when making a geo-json query") {
    import gen.Headers._

    forAll { (reqId: RequestId,
              id: String,
              param: (String, String),
              knownHeader: IncomingHeader,
              unknownHeader: UnknownHeader) =>
      val base = RequestBuilder("mock.socrata.com")
      val request = mocks.StaticRequest(param, Map(knownHeader, unknownHeader))

      val expected = base.
        path(Seq("id", s"$id.geojson")).
        addHeader(ReqIdHeader -> reqId).
        addHeader(knownHeader).
        query(Map(param)).
        get.builder

      val client = mocks.StaticClient.withReq { request =>
        val actual = request(base).builder

        // The assertions are here, because of weird inversion of control.
        actual.url must equal (expected.url)
        actual.method must equal (expected.method)
        actual.query.toSet must equal (expected.query.toSet)
        actual.headers.toSet must equal (expected.headers.toSet)

        mock[Response]
      }

      TileService(client).geoJsonQuery(reqId,
                                       request,
                                       id,
                                       Map(param),
                                       Unused): Unit
    }
  }

  test("Correct returned headers are surfaced when processing response") {
    import gen.Extensions._
    import gen.Headers._

    forAll { (known: OutgoingHeader,
              unknown: UnknownHeader,
              ext: Extension) =>
      val (k, v): (String, String) = known
      val upstream = mocks.HeaderResponse(Map(known, unknown))
      val resp = new mocks.ByteArrayServletOutputStream().responseFor

      TileService(Unused).processResponse(Unused, ext)(upstream)(resp)

      verify(resp).setStatus(ScOk)
      verify(resp).setHeader("Access-Control-Allow-Origin", "*")
      verify(resp).setHeader(k, v)
    }
  }

  test("Features are encoded according to extension when processing response") {
    import gen.Extensions._
    import gen.Points._

    forAll { (pt: ValidPoint, ext: Extension) =>
      val upstream = mocks.SeqResponse(Seq(fJson(pt)))
      val expected = Set(feature(pt))
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      TileService(Unused).processResponse(Unused, ext)(upstream)(resp)

      verify(resp).setStatus(ScOk)

      ext match {
        case Pbf =>
          outputStream.getBytes must includeSlice (TileEncoder(expected).bytes)
        case BPbf =>
          outputStream.getString must include (TileEncoder(expected).base64)
        case Json =>
          outputStream.getString must equal (upstream.toString)
        // ".txt" should be supported, but its output format is unspecified.
        case Txt => ()
      }
    }
  }

  test("Invalid json returns 'internal server error' when processing response") {
    import gen.Extensions._

    forAll { (message: String, ext: Extension) =>
      val upstream = mocks.StringResponse("{")
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      TileService(Unused).processResponse(Unused, ext)(upstream)(resp)

      verify(resp).setStatus(ScInternalServerError)

      outputStream.getLowStr must include ("message")
      outputStream.getLowStr must include ("invalid")
      outputStream.getLowStr must include ("json")
      outputStream.getLowStr must include ("underlying")
    }
  }

  test("Invalid geo-json returns 'internal server error' when processing response") {
    import gen.Extensions._

    forAll { (message: String, ext: Extension) =>
      val upstream = mocks.StringResponse(json"""{"invalidKey": $message}""")
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      TileService(Unused).processResponse(Unused, ext)(upstream)(resp)

      verify(resp).setStatus(ScInternalServerError)

      outputStream.getLowStr must include ("message")
      outputStream.getLowStr must include ("invalid")
      outputStream.getLowStr must include ("json")
      outputStream.getLowStr must include ("underlying")
      outputStream.getString must include (encode(message))
    }
  }

  test("Unknown errors are handled when processing response") {
    import gen.Extensions._

    forAll { (message: String, ext: Extension) =>
      val upstream = mocks.ThrowsResponse(message)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      TileService(Unused).processResponse(Unused, ext)(upstream)(resp)

      verify(resp).setStatus(ScInternalServerError)

      outputStream.getLowStr must include ("unknown")
      outputStream.getLowStr must include ("error")
      outputStream.getString must include (encode(message))
    }
  }

  test("Handle request returns OK when underlying succeeds") {
    import gen.Extensions._
    import gen.Points._

    forAll { (pt: ValidPoint, ext: Extension) =>
      val upstream = mocks.SeqResponse(Seq(fJson(pt)))
      val client = mocks.StaticClient(upstream)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      TileService(client).handleRequest(Unused, Unused, Unused, Unused, ext)(resp)

      verify(resp).setStatus(ScOk)

      if (ext == Json) {
        outputStream.getString must include (upstream.toString)
      }
    }
  }

  test("Handle request returns OK when underlying succeeds for single FeatureJson") {
    import gen.Extensions._
    import gen.Points._
    import com.socrata.thirdparty.geojson.GeoJson

    forAll { (pt: ValidPoint, ext: Extension) =>
      val s = GeoJson.codec.encode(fJson(pt)).toString.replaceAll("\\s*", "")
      val upstream = mocks.StringResponse(s)
      val client = mocks.StaticClient(upstream)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      TileService(client).handleRequest(Unused, Unused, Unused, Unused, ext)(resp)

      verify(resp).setStatus(ScOk)

      if (ext == Json) {
        outputStream.getString must include (s.toString)
      }
    }
  }

  test("Handle request returns 304 with no body when given 304.") {
    val upstream = mock[Response]
    when(upstream.resultCode).thenReturn(ScNotModified)

    val client = mocks.StaticClient(upstream)
    val outputStream = new mocks.ByteArrayServletOutputStream
    val resp = outputStream.responseFor

    TileService(client).handleRequest(Unused, Unused, Unused, Unused, Unused)(resp)

    verify(resp).setStatus(ScNotModified)

    outputStream.getString must have length (0)
  }

  test("Handle request echos known codes") {
    import gen.StatusCodes._

    forAll { (statusCode: KnownStatusCode, payload: String) =>
      val message = s"""{message: ${encode(payload)}}"""
      val upstream = mock[Response]
      when(upstream.resultCode).thenReturn(statusCode)
      when(upstream.inputStream(anyInt)).thenReturn(mocks.StringInputStream(message))

      val client = mocks.StaticClient(upstream)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      TileService(client).handleRequest(Unused, Unused, Unused, Unused, Unused)(resp)

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

      val client = mocks.StaticClient(upstream)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      TileService(client).handleRequest(Unused, Unused, Unused, Unused, Unused)(resp)

      verify(resp).setStatus(ScInternalServerError)

      outputStream.getLowStr must include ("underlying")
      outputStream.getString must include (encode(payload))
      outputStream.getString must include (statusCode.toString)
    }
  }

  test("Handle request returns 'internal server error' if processing throws") {
    import gen.Extensions._

    forAll { (message: String, ext: Extension) =>
      val client = mocks.StaticClient {
        () => throw new RuntimeException(message)
      }

      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      TileService(client).handleRequest(Unused, Unused, Unused, Unused, ext)(resp)

      verify(resp).setStatus(ScInternalServerError)

      outputStream.getLowStr must include ("unknown error")
      outputStream.getString must include (encode(message))
    }
  }

  test("Get returns success when underlying succeeds") {
    import gen.Extensions._
    import gen.Points._

    forAll { (pt: ValidPoint, ext: Extension) =>
      val upstream = mocks.SeqResponse(Seq(fJson(pt)))
      val client = mocks.StaticClient(upstream)
      val outputStream = new mocks.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      TileService(client).
        service(Unused,
                Unused,
                Unused,
                Unused,
                TypedPathComponent(Unused, ext)).
        get(Unused)(resp)

      verify(resp).setStatus(ScOk)

      if (ext == Json) {
        outputStream.getString must include (upstream.toString)
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

      verify(resp).setStatus(ScInternalServerError)
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
    val otherKey = "$other"
    val whereKey = "$where"
    val selectKey = "$select"

    forAll { (rawOtherValue: String,
              rawWhereBase: String,
              rawWhereValue: String,
              rawSelectBase: String,
              rawSelectValue: String) =>
      val otherValue = encode(rawOtherValue) filter (_.isLetterOrDigit)
      val whereBase = encode(rawWhereBase) filter (_.isLetterOrDigit)
      val whereValue = encode(rawWhereValue) filter (_.isLetterOrDigit)
      val selectBase = encode(rawSelectBase) filter (_.isLetterOrDigit)
      val selectValue = encode(rawSelectValue) filter (_.isLetterOrDigit)

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

  test("An empty list of coordinates rolls up correctly") {
    TileService.rollup(Unused, Iterator.empty) must be (Set.empty)
  }

  test("A single coordinate rolls up correctly") {
    forAll { pt: (Int, Int) =>
      TileService.rollup(Unused, Iterator.single(fJson(pt))) must equal (Set(feature(pt)))
    }
  }

  test("Unique coordinates are included when rolled up") {
    forAll { (pt0: (Int, Int), pt1: (Int, Int), pt2: (Int, Int)) =>
      whenever (uniq(pt0, pt1, pt2)) {
        val coordinates = Seq(fJson(pt0),
                              fJson(pt1),
                              fJson(pt2))
        val expected = Set(feature(pt0),
                           feature(pt1),
                           feature(pt2))
        val actual = TileService.rollup(Unused, coordinates.toIterator)

        actual must equal (expected)
      }
    }
  }

  test("Coordinates have correct counts when rolled up") {
    forAll { (pt0: (Int, Int), pt1: (Int, Int), pt2: (Int, Int)) =>
      whenever (uniq(pt0, pt1, pt2)) {
        val coordinates = Seq(fJson(pt0),
                              fJson(pt1),
                              fJson(pt1),
                              fJson(pt2),
                              fJson(pt2))
        val expected = Set(feature(pt0, count=1),
                           feature(pt1, count=2),
                           feature(pt2, count=2))
        val actual = TileService.rollup(Unused, coordinates.toIterator)

        actual must equal (expected)
      }
    }
  }

  test("Coordinates with unique properties are not rolled up") {
    forAll { (pt0: (Int, Int),
              pt1: (Int, Int),
              prop0: (String, String),
              prop1: (String, String)) =>
      val (k0, _) = prop0
      val (k1, _) = prop1

      whenever (pt0 != pt1 && prop0 != prop1 && k0 != k1) {
        val coordinates = Seq(fJson(pt0, Map(prop0)),
                              fJson(pt0, Map(prop0, prop1)),
                              fJson(pt0, Map(prop1)),
                              fJson(pt1, Map(prop1)),
                              fJson(pt1, Map(prop1)))
        val expected = Set(feature(pt0, 1, Map(prop0)),
                           feature(pt0, 1, Map(prop0, prop1)),
                           feature(pt0, 1, Map(prop1)),
                           feature(pt1, 2, Map(prop1)))

        val actual = TileService.rollup(Unused, coordinates.toIterator)

        actual must equal (expected)
      }
    }
  }
}
