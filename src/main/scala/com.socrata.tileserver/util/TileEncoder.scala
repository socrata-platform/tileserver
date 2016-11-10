package com.socrata.tileserver.util

import com.rojoma.json.v3.ast.{JArray, JBoolean, JNull, JNumber, JObject, JString, JValue}
import com.rojoma.json.v3.codec.JsonEncode.toJValue
import com.rojoma.json.v3.util.JsonUtil
import com.socrata.soql.environment.ColumnName

import com.vividsolutions.jts.geom.{Geometry, Point}
import com.vividsolutions.jts.io.WKBWriter
import com.vividsolutions.jts.util.AssertionFailedException
import no.ecc.vectortile.VectorTileEncoder
import org.apache.commons.codec.binary.Base64
import org.slf4j.{Logger, LoggerFactory}

import TileEncoder._
import com.socrata.tileserver.util.RenderProvider.MapTile
import scala.collection.JavaConverters._

/** Encodes features in a variety of formats.
  *
  * @constructor create a new encoder for the given features.
  * @param features the features to encode.
  */
case class TileEncoder(features: Set[TileEncoder.Feature]) {
  private lazy val writer: WKBWriter = new WKBWriter()

  /** Create a vector tile encoded as a protocol-buffer. */
  lazy val bytes: Array[Byte] = {
    val underlying = new VectorTileEncoder(ZoomFactor * CoordinateMapper.Size,
                                           ZoomFactor * CoordinateMapper.Size,
                                           true)

    features foreach { case (geometry, attributes) =>
      try {
        underlying.addFeature(layerName(geometry), attributes.asJava, geometry)
      } catch {
        // $COVERAGE-OFF$ Not worth injecting the VectorTileEncoder.
        case e: AssertionFailedException =>
          logger.warn("Invalid geometry", geometry)
          logger.warn(e.getMessage, e)
          // $COVERAGE-ON$
      }
    }

    underlying.encode()
  }

  /** Create a vector tile as a base64 encoded protocol-buffer. */
  lazy val base64: String = Base64.encodeBase64String(bytes)

  /** Create a Seq of Well Known Binary geometries and their attributes. */
  lazy val mapTile: MapTile = {
    val grouped = features.toSeq.groupBy { case (geom, _) => layerName(geom) }

    grouped.map { case (layer, features) =>
      layer -> features.map {
        case (geom: Geometry, rawAttrs) =>
          val wkbs = Map("wkbs" -> writer.write(geom))

          val attributes = rawAttrs.get("properties").map { properties =>
            Map("attributes" -> jValueToScala(properties))
          }.getOrElse(Map.empty)

          wkbs ++ attributes
      }
    }
  }

  /** String representation of `features`. */
  override lazy val toString: String = {
    features map {
      case (geometry, attributes) =>
        s"#${layerName(geometry)} \t geometry: $geometry \t attributes: ${toJValue(attributes)}"
    } mkString "\n"
  }
}

object TileEncoder {
  private val logger: Logger = LoggerFactory.getLogger(getClass)
  private val ZoomFactor: Int = 16
  private val minLong = BigDecimal(Long.MinValue)
  private val maxLong = BigDecimal(Long.MaxValue)

  /** (geometry, attributes) */
  type Feature = (Geometry, Map[String, JValue])

  /** Return a JValue converted into Scala types (primitives, collections, etc). */
  def jValueToScala(jVal: JValue): Any = jVal match {
    case JObject(features) => features.mapValues(jValueToScala).toMap
    case JArray(underlying) => underlying.map(jValueToScala)
    case JBoolean(underlying) => underlying
    case JString(underlying) => underlying
    case JNull => null // scalastyle:ignore
    case num: JNumber =>
      val decimal = num.toBigDecimal
      if (decimal <= maxLong && decimal >= minLong && decimal.bigDecimal.stripTrailingZeros.scale <= 0) {
        num.toLong
      } else {
        decimal
      }
  }

  def layerName(geom: Geometry): String = geom match {
    case _: Point => "main"
    case _ => geom.getGeometryType.toLowerCase
  }
}
