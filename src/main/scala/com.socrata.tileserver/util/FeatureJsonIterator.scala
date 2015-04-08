package com.socrata.tileserver
package util

import java.io.DataInputStream

import com.socrata.thirdparty.geojson.FeatureJson
import com.vividsolutions.jts.io.WKBReader
import org.slf4j.LoggerFactory
import org.velvia.{MsgPack, InvalidMsgPackDataException}
import org.velvia.MsgPackUtils._

/**
 * An Iterator that streams in FeatureJson's from a SoQLPack binary stream.
 * Note that there is no good way to detect the end of a stream, other than to try reading from
 * it in hasNext and cache the results.....
 */
class FeatureJsonIterator(reader: WKBReader,
                          dis: DataInputStream,
                          geomIndex: Int) extends Iterator[FeatureJson] {
  private val logger = LoggerFactory.getLogger(getClass)

  var nextRow: Option[Seq[Any]] = None

  logger.info("Created FeatureIterator")

  // Only allow nextRow to be filled once
  // NOTE: if IOException is raised during this, make sure the stream hasn't been closed prior
  // to reading from it.
  def hasNext: Boolean = {
    if (!nextRow.isDefined) {
      try {
        nextRow = Some(MsgPack.unpack(dis, 0).asInstanceOf[Seq[Any]])
      } catch {
        case e: InvalidMsgPackDataException =>
          logger.info("Probably reached end of data, got " + e)
          nextRow = None
      }
    }
    nextRow.isDefined
  }

  def next(): FeatureJson = {
    val row = nextRow.get
    nextRow = None    // MUST reset to avoid an endless loop
    val geom = reader.read(row(geomIndex).asInstanceOf[Array[Byte]])
    // TODO: parse other columns as properties.  For now just skip it
    FeatureJson(Map(), geom)
  }
}
