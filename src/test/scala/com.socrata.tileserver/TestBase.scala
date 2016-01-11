package com.socrata.tileserver

import org.scalatest._
import org.scalatest.matchers._
import org.scalatest.prop.PropertyChecks

import com.rojoma.json.v3.ast.JString
import com.rojoma.json.v3.codec.JsonEncode.toJValue
import com.vividsolutions.jts.geom.{Coordinate, GeometryFactory, Point}

import com.socrata.http.server.HttpRequest
import com.socrata.testcommon.ResponseSugar
import com.socrata.thirdparty.geojson.FeatureJson

import UnusedSugar._
import util.TileEncoder.Feature

// scalastyle:off import.grouping
trait TestBase
    extends FunSuite
    with org.scalatest.MustMatchers
    with ResponseSugar
    with PropertyChecks
    with BeforeAndAfterEach {
  val GeomFactory = new GeometryFactory()

  def fJson(): FeatureJson = fJson((Unused, Unused): (Int, Int))

  def fJson(pt: (Int, Int),
            attributes: Map[String, String] = Map.empty): FeatureJson = {
    val attributesAsJvalues = attributes map { case (k, v) => (k, toJValue(v)) }
    FeatureJson(attributesAsJvalues, point(pt))
  }

  def feature(ptct: (gen.Points.ValidPoint, Int)): Feature = {
    import gen.Points._

    val (pt, ct) = ptct
    feature(pt, count=ct)
  }

  def feature(pt: (Int, Int),
              count: Int = 1,
              attributes: Map[String, String] = Map.empty): Feature = {
    (point(pt), Map("count" -> toJValue(count)) ++
       Map("properties" -> toJValue(attributes)))
  }

  def encode(s: String): String = JString(s).toString

  def point(pt: (Int, Int)): Point = {
    val (x, y) = pt

    GeomFactory.createPoint(new Coordinate(x, y))
  }

  class ArraySliceIncludeMatcher[T](expected: Array[T]) extends Matcher[Array[T]] {
    def apply(actual: Array[T]): MatchResult = {
      MatchResult(expected.containsSlice(actual),
                  s"""Array "$actual" did not include "$expected" as a slice""",
                  s"""Array "$actual" included "$expected" as a slice""")
    }
  }

  def includeSlice[T](expected: Array[T]): Matcher[Array[T]] = new ArraySliceIncludeMatcher(expected)

  override def beforeEach: Unit = resourceScope.close()
  override def afterEach: Unit = resourceScope.close()

  def reqInfo(req: HttpRequest,
              ext: String = Unused,
              geoColumn: String = Unused): util.RequestInfo =
    util.RequestInfo(req, Unused, geoColumn, Unused, ext)

  def reqInfo(ext: String): util.RequestInfo =
    util.RequestInfo(Unused, Unused, Unused, Unused, ext)

  def reqInfo(req: HttpRequest,
              datasetId: String,
              geoColumn: String,
              tile: util.QuadTile,
              ext: String): util.RequestInfo =
    util.RequestInfo(req, datasetId, geoColumn, tile, ext)
}
