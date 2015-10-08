package com.socrata.tileserver.util

import scala.collection.JavaConverters._

import com.rojoma.json.v3.ast.JValue
import com.rojoma.json.v3.codec.JsonEncode.toJValue
import com.vividsolutions.jts.geom.{Geometry, Point}
import com.vividsolutions.jts.io.WKBWriter
import com.vividsolutions.jts.util.AssertionFailedException
import no.ecc.vectortile.VectorTileEncoder
import org.apache.commons.codec.binary.Base64
import org.slf4j.{Logger, LoggerFactory}

import TileEncoder._

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

  /** Create a Seq of Well Known Binary geometries. */
  lazy val wkbs: Map[String, Seq[Array[Byte]]] = {
    val grouped = features.toSeq.groupBy { case (geom, _) => layerName(geom) }

    grouped.map { case (layer, features) =>
      layer -> features.map { case (geom, _) => writer.write(geom) }
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

  /** (geometry, attributes) */
  type Feature = (Geometry, Map[String, JValue])

  def layerName(geom: Geometry): String = geom match {
    case _: Point => "main"
    case _ => geom.getGeometryType.toLowerCase
  }
}
