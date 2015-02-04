package com.socrata.tileserver
package util

import CoordinateMapper.Size

/** A bounding box (tile) on a map.
  *
  * @constructor create a new tile for the given coordinates.
  * @param rawX raw x coordinate (before mapping to tms)
  * @param rawY raw y coordinate (before mapping to tms)
  * @param zoom zoom level of the map
  */
case class QuadTile(rawX: Int, rawY: Int, zoom: Int) {
  /** The mapper for this zoom. **/
  val mapper = CoordinateMapper(zoom)

  /** The mapped TMS coordinate. */
  val (x: Int, y: Int) = mapper.tmsCoordinates(rawX, rawY)

  /** North edge of the tile (lat). */
  val north: Double = mapper.lat(y * Size)

  /** East edge of the tile (lon). */
  val east:  Double = mapper.lon(x * Size + Size - 1)

  /** South edge of the tile (lat). */
  val south: Double = mapper.lat(y * Size + Size - 1)

  /** West edge of the tile (lon). */
  val west:  Double = mapper.lon(x * Size)

  /** Return the within_box SoQL fragment for the given column.
    *
    * @param pointColumn the column to match against.
    */
  def withinBox(pointColumn: String): String = {
    s"within_box($pointColumn, $north, $west, $south, $east)"
  }
}
