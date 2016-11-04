package com.socrata.tileserver
package util

import java.io.{ByteArrayInputStream, DataInputStream}

import com.rojoma.json.v3.codec.{DecodeError, JsonCodec, JsonDecode}
import com.rojoma.json.v3.codec.JsonEncode.toJValue

import com.rojoma.json.v3.util.{AutomaticJsonCodecBuilder, JsonUtil}
import com.rojoma.simplearm.v2.ResourceScope
import org.apache.commons.io.IOUtils
import org.velvia.InvalidMsgPackDataException

import com.socrata.http.client.{Response, ResponseInfo}
import com.socrata.http.server.responses._
import com.socrata.soql.{SoQLPackIterator, SoQLGeoRow}
import com.socrata.thirdparty.geojson.FeatureJson

import TileEncoder.Feature
import exceptions._

/** Wraps a geometry response from the underlying service. */
trait GeoResponse extends ResponseInfo {
  override def resultCode: Int
  override def headers(name: String): Array[String]
  override def headerNames: Set[String]

  /** The resource scope to use when processing the response. */
  protected def resourceScope: ResourceScope

  /** The (binary) payload from the underlying response. */
  def payload: Array[Byte]

  /** The unpacked features without any processing. */
  def rawFeatures: Iterator[FeatureJson] = {
    if (resultCode != OK.statusCode) {
      throw new IllegalStateException("Tried to unpack failed response!")
    }

    try {
      val dis = resourceScope.
        open(new DataInputStream(new ByteArrayInputStream(payload)))
      val soqlIter = new SoQLPackIterator(dis)
      if (soqlIter.geomIndex < 0) {
        throw InvalidSoqlPackException(
          s"No geometry present or other header error: ${soqlIter.headers}")
      }

      val geoRows = soqlIter.map(new SoQLGeoRow(_, soqlIter))

      geoRows.filter(_.geometry.isDefined).map { row =>
        FeatureJson(row.properties, row.geometry.get)
      }
    } catch {
      case e @ (_: InvalidMsgPackDataException |
                  _: ClassCastException |
                  _: NoSuchElementException |
                  _: NullPointerException) =>
        throw InvalidSoqlPackException("Unable to parse binary stream into SoQLPack/MessagePack records", e)
    }
  }

  /** The features after they have been processed (de-duplicated).
    *
    * @param tile which `QuadTile` we are mapping features onto.
    */
  def features(tile: QuadTile, flip: Boolean): Set[Feature] = {
    val pairs: Iterator[Feature] = rawFeatures.map { f =>
      f.geometry.apply(tile.filter(flip))
      f.geometry.geometryChanged()
      f.geometry -> f.properties
    }

    val pxCounts = pairs.foldLeft(Map[Feature, Int]()) { (acc, item) =>
      val ct = acc.getOrElse(item, 0) + 1
      acc + (item -> ct)
    }

    pxCounts.map { case ((geom, props), count) =>
      geom -> Map("count" -> toJValue(count), "properties" -> toJValue(props))
    } (collection.breakOut) // Build `Set` not `Seq`.
  }
}

object GeoResponse {
  /** Create a `GeoResponse` from an underlying response.
    *
    * @param underlying the underlying response.
    * @param rs the resource scope to use when processing the response.
    */
  def apply(underlying: Response, rs: ResourceScope): GeoResponse =
    new GeoResponseImpl(underlying, rs)

  class GeoResponseImpl private[GeoResponse] (underlying: Response, rs: ResourceScope)
      extends GeoResponse {
    protected val resourceScope = rs

    val payload: Array[Byte] = {
      val is = underlying.inputStream()
      if (is != null) IOUtils.toByteArray(is) else Array.empty // scalastyle:ignore
    }

    val resultCode = underlying.resultCode
    def headers(name: String): Array[String] = underlying.headers(name)
    val headerNames: Set[String] = underlying.headerNames
  }
}
