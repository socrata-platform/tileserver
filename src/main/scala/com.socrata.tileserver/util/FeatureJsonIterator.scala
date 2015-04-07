package com.socrata.tileserver
package util

import java.io.DataInputStream

import com.socrata.thirdparty.geojson.FeatureJson
import com.vividsolutions.jts.io.WKBReader
import org.velvia.MsgPack
import org.velvia.MsgPackUtils._

class FeatureJsonIterator(reader: WKBReader,
                          dis: DataInputStream,
                          geomIndex: Int) extends Iterator[FeatureJson] {
  def hasNext: Boolean = dis.available > 0
  def next(): FeatureJson = {
    val row = MsgPack.unpack(dis, 0).asInstanceOf[Seq[Any]]
    val geom = reader.read(row(geomIndex).asInstanceOf[Array[Byte]])
    // TODO: parse other columns as properties.  For now just skip it
    FeatureJson(Map(), geom)
  }
}
