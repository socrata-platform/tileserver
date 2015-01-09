package com.socrata.tileserver

import org.scalatest.prop.PropertyChecks
import org.scalatest.{FunSuite, MustMatchers}

import com.rojoma.json.v3.ast.JString
import com.rojoma.json.v3.codec.JsonEncode.toJValue
import com.rojoma.json.v3.conversions._
import com.vividsolutions.jts.geom.{Coordinate, GeometryFactory, Point}

import com.socrata.thirdparty.geojson.FeatureJson

import services.TileService

trait TestBase extends FunSuite with MustMatchers with PropertyChecks {
  val GeomFactory = new GeometryFactory()

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

  def encode(s: String): String = JString(s).toString

  def uniq(objs: AnyRef*): Boolean = Set(objs: _*).size == objs.size

  def point(pt: (Int, Int)): Point = {
    val (x, y) = pt

    GeomFactory.createPoint(new Coordinate(x, y))
  }
}
