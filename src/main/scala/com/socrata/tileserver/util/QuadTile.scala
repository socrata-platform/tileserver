package com.socrata.tileserver.util

import CoordinateMapper.Size

case class QuadTile(rawX: Int, rawY: Int, zoom: Int) {
  val mapper = CoordinateMapper(zoom)
  val (x: Int, y: Int) = mapper.tmsCoordinates(rawX, rawY)

  val north: Double = mapper.lat(y * Size)
  val east:  Double = mapper.lon(x * Size + Size - 1)
  val south: Double = mapper.lat(y * Size + Size - 1)
  val west:  Double = mapper.lon(x * Size)

  def withinBox(pointColumn: String): String = {
    s"within_box($pointColumn, $north, $west, $south, $east)"
  }
}
