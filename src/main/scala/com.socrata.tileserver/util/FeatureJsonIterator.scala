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
class FeatureJsonIterator(dis: DataInputStream,
                          geomIndex: Int) extends Iterator[FeatureJson] {
  private val logger = LoggerFactory.getLogger(getClass)
  private val reader = new WKBReader

  var nextRow: Option[Seq[Any]] = None
  var rowNum = 0

  logger.debug("Created FeatureIterator")

  // Only allow nextRow to be filled once
  // NOTE: if IOException is raised during this, make sure the stream hasn't been closed prior
  // to reading from it.
  def hasNext: Boolean = {
    if (!nextRow.isDefined) {
      try {
        logger.trace("Unpacking row {}", rowNum)
        nextRow = Some(MsgPack.unpack(dis, 0).asInstanceOf[Seq[Any]])
        rowNum += 1
      } catch {
        case e: InvalidMsgPackDataException =>
          logger.debug("Probably reached end of data at rowNum {}, got {}", rowNum, e.getMessage)
          nextRow = None
        case e: Exception =>
          logger.error("Unexpected exception at rowNum {}", rowNum, e)
          throw e
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
