package com.socrata.tileserver
package services

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse.{SC_BAD_REQUEST => ScBadRequest}
import scala.util.control.NoStackTrace

import com.rojoma.json.v3.ast.{JString, JValue}
import com.rojoma.json.v3.codec.JsonEncode.toJValue
import com.rojoma.json.v3.conversions._
import com.socrata.http.server.util.RequestId.ReqIdHeader
import com.vividsolutions.jts.geom.{Coordinate, Geometry, GeometryFactory, Point}
import org.mockito.Mockito.{verify, when}
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, MustMatchers}
import org.slf4j.Logger

import com.socrata.http.server.HttpRequest
import com.socrata.http.server.HttpRequest.AugmentedHttpServletRequest
import com.socrata.thirdparty.geojson.FeatureJson

import util.{CoordinateMapper, MapRequest, SeqResponse, StringEncoder}

class TileServiceTest
    extends FunSuite
    with MustMatchers
    with PropertyChecks
    with MockitoSugar {
  val logger: Logger = mock[Logger]
  val geomFactory = new GeometryFactory()
  val echoMapper = new CoordinateMapper(0) {
    override def tilePx(lon: Double, lat:Double): (Int, Int) =
      (lon.toInt, lat.toInt)
  }

  def uniq(objs: AnyRef*): Boolean = Set(objs: _*).size == objs.size

  def encode(s: String): String = JString(s).toString

  def point(pt: (Int, Int)): Point = {
    val (x, y) = pt

    geomFactory.createPoint(new Coordinate(x, y))
  }

  def fJson(pt: (Int, Int),
            attributes: Map[String, String] = Map.empty): FeatureJson = {
    val attributesV2 = attributes map { case (k, v) => (k, toJValue(v).toV2) }
    FeatureJson(attributesV2, point(pt))
  }

  def feature(pt: (Int, Int),
              count: Int = 1,
              attributes: Map[String, String] = Map.empty): TileService.Feature = {
    (point(pt), Map("count" -> toJValue(count)) ++
       Map("properties" -> toJValue(attributes)))
  }

  test("Bad request must include message and cause") {
    forAll { (message: String, causeMessage: String) =>
      val outputStream = new util.ByteArrayServletOutputStream
      val resp = outputStream.responseFor
      val cause = new NoStackTrace {
        override def getMessage: String = causeMessage
      }

      TileService.badRequest(message, cause)(logger)(resp)

      verify(resp).setStatus(ScBadRequest)
      verify(resp).setContentType("application/json; charset=UTF-8")

      outputStream.getLowStr must include ("message")
      outputStream.getString must include (encode(message))
      outputStream.getLowStr must include ("cause")
      outputStream.getString must include (encode(causeMessage))
    }
  }

  test("Bad request must include message and info") {
    forAll { (message: String, info: String) =>
      val outputStream = new util.ByteArrayServletOutputStream
      val resp = outputStream.responseFor

      TileService.badRequest(message, info)(logger)(resp)

      verify(resp).setStatus(ScBadRequest)
      verify(resp).setContentType("application/json; charset=UTF-8")

      outputStream.getLowStr must include ("message")
      outputStream.getString must include (encode(message))
      outputStream.getLowStr must include ("info")
      outputStream.getString must include (encode(info))
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

      val neither = MapRequest(otherKey -> otherValue)
      val where = MapRequest(whereKey -> whereBase)
      val select = MapRequest(selectKey -> selectBase)

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
    val noFeatures = Seq.empty
    TileService.rollup(echoMapper, noFeatures) must be (Set.empty)
  }

  test("A single coordinate is rolls up correctly") {
    forAll { pt: (Int, Int) =>
      TileService.rollup(echoMapper, Seq(fJson(pt))) must equal (Set(feature(pt)))
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
        val actual = TileService.rollup(echoMapper, coordinates)

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
        val actual = TileService.rollup(echoMapper, coordinates)

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

        val actual = TileService.rollup(echoMapper, coordinates)

        actual must equal (expected)
      }
    }
  }

  test("Encoder includes all features") {
    forAll { (pt0: (Int, Int),
              pt1: (Int, Int),
              pt2: (Int, Int),
              prop0: (String, String),
              prop1: (String, String)) =>
      val (k0, _) = prop0
      val (k1, _) = prop1

      whenever (uniq(pt0, pt1, pt2) && prop0 != prop1 && k0 != k1) {
        val coordinates = Seq(fJson(pt0),
                              fJson(pt1),
                              fJson(pt2))
        val expected = Set(feature(pt0),
                           feature(pt1),
                           feature(pt2))

        val bytes = TileService.encoder(echoMapper)(StringEncoder())(SeqResponse(coordinates))
        bytes must be ('defined)
        bytes.get.length must be > 0
        new String(bytes.get, "UTF-8") must equal (StringEncoder.encFeatures(expected))
      }
    }
  }
}
