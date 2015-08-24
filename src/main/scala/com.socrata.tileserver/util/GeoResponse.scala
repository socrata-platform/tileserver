package com.socrata.tileserver
package util

import java.io.{ByteArrayInputStream, DataInputStream}
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}

import com.rojoma.json.v3.codec.JsonEncode.toJValue
import com.rojoma.simplearm.v2.ResourceScope
import org.apache.commons.io.IOUtils
import org.velvia.InvalidMsgPackDataException

import com.socrata.http.client.{Response, ResponseInfo}
import com.socrata.soql.{SoQLPackIterator, SoQLGeoRow}
import com.socrata.thirdparty.geojson.FeatureJson

import TileEncoder.Feature
import exceptions._

trait GeoResponse extends ResponseInfo {
  protected def resourceScope: ResourceScope
  def payload: Array[Byte]
  def resultCode: Int
  def headers(name: String): Array[String]
  def headerNames: Set[String]

  def rawFeatures: Iterator[FeatureJson] = {
    if (resultCode != ScOk) {
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

      soqlIter.map { row =>
        val soqlRow = new SoQLGeoRow(row, soqlIter)
        FeatureJson(soqlRow.properties, soqlRow.geometry)
      }
    } catch {
      case e @ (_: InvalidMsgPackDataException |
                  _: ClassCastException |
                  _: NoSuchElementException |
                  _: NullPointerException) =>
        throw InvalidSoqlPackException("Unable to parse binary stream into SoQLPack/MessagePack records", e)
    }
  }

  def features(tile: QuadTile): Set[Feature] = {
    val pairs: Iterator[Feature] = rawFeatures.map { f =>
      f.geometry.apply(tile)
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
