package com.socrata.tileserver
package util

import com.vividsolutions.jts.geom.{Coordinate, CoordinateFilter}

import CoordinateMapper.Size

/** A bounding box (tile) on a map.
  *
  * @constructor create a new tile for the given coordinates.
  * @param rawX raw x coordinate (before mapping to tms)
  * @param rawY raw y coordinate (before mapping to tms)
  * @param zoom zoom level of the map
  */
case class QuadTile(rawX: Int, rawY: Int, zoom: Int) extends CoordinateFilter {
  /** The mapper for this zoom. */
  val mapper = CoordinateMapper(zoom)

  /** The mapped TMS coordinate. */
  val (x: Int, y: Int) = mapper.tmsCoordinates(rawX, rawY)

  /** North edge of the tile (lat). */
  val north: Double = mapper.lat(y * Size)

  /** East edge of the tile (lon). */
  val east:  Double = mapper.lon(x * Size + Size)

  /** South edge of the tile (lat). */
  val south: Double = mapper.lat(y * Size + Size)

  /** West edge of the tile (lon). */
  val west:  Double = mapper.lon(x * Size)

  /** Return the within_box SoQL fragment for the given column.
    *
    * @param pointColumn the column to match against.
    */
  def withinBox(pointColumn: String): String = {
    s"within_box($pointColumn, $north, $west, $south, $east)"
  }

  /** The pixel (x, y) corresponding * to "lon" and "lat" in 256x256 space. */
  def px(lon: Double, lat: Double): (Int, Int) = {
    val (lonX, latY) = mapper.px(lon, lat)

    (lonX - (rawX * Size), latY - (rawY * Size))
  }

  /** Map a (lon, lat) coordinate into (x, y) in 256x256 space. */
  def px(c: Coordinate): Coordinate = {
    val (x, y) = px(c.x, c.y)
    new Coordinate(x, y)
  }

  /** Map a (lon, lat) coordinate into (x, y) in 256x256 space.
    *
    * NOTE: This mutates the provided coordinate. */
  def filter(c: Coordinate): Unit = {
    val mapped = px(c)
    c.setCoordinate(mapped)
  }
}
