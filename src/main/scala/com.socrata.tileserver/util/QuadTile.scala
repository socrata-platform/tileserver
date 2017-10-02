package com.socrata.tileserver
package util

import com.vividsolutions.jts.geom.{Coordinate, CoordinateFilter}

import CoordinateMapper.Size
import QuadTile._

/** A bounding box (tile) on a map.
  *
  * @constructor create a new tile for the given coordinates.
  * @param rawX raw x coordinate (before mapping to tms)
  * @param rawY raw y coordinate (before mapping to tms)
  * @param zoom zoom level of the map
  */
case class QuadTile(rawX: Int, rawY: Int, zoom: Int) {
  /** The mapper for this zoom. */
  val mapper = CoordinateMapper(zoom)

  private val WorldWidth = 1 << zoom
  private def center(n: Int): Int = ((n % WorldWidth) + WorldWidth) % WorldWidth

  /** The mapped TMS coordinate. */
  val (x: Int, y: Int) = mapper.tmsCoordinates(center(rawX), center(rawY))

  /** North edge of the tile (lat).
    *
    * @param os overscan amount (in pixels)
    */
  def north(os: Int): Double = north(os, y)
  private def north(os: Int, y: Int): Double = mapper.lat(y * Size - os)

  /** East edge of the tile (lon).
    *
    * @param os overscan amount (in pixels)
    */
  def east(os: Int):  Double = mapper.lon(x * Size + Size + os)

  /** South edge of the tile (lat).
    *
    * @param os overscan amount (in pixels)
    */
  def south(os: Int): Double = south(os, y)
  private def south(os: Int, y: Int): Double = mapper.lat(y * Size + Size + os)

  /** West edge of the tile (lon).
    *
    * @param os overscan amount (in pixels)
    */
  def west(os: Int):  Double = mapper.lon(x * Size - os)

  /** The width of pixels in degrees (lat/lon). */
  val resolution: Double = (south(0, 0) - north(0, 0)) / Size

  /** The point (x, y) in tile (256x256) space.
    *
    * @param lon the longitude of the point.
    * @param lat the latitude of the point.
    */
  def px(lon: Double, lat: Double, flip: Boolean): (Int, Int) = {
    val (lonX, latY) = mapper.px(lon, lat)

    val unflipped = latY - (rawY * Size)
    val pxY = if (flip) (Size - 1) - unflipped else unflipped
    (lonX - (rawX * Size), pxY)
  }

  /** The point (x, y) in tile (256x256) space.
    *
    * @param c the lat and lon of the point.
    */
  def px(c: Coordinate, flip: Boolean): Coordinate = {
    val (x, y) = px(c.x, c.y, flip)
    new Coordinate(x, y)
  }

  /** A coordinate filter, to map the point (x, y) in tile (256x256) space. */
  def filter(flip: Boolean): TileFilter = TileFilter(this, flip)

  /** A Seq of the corners (lon, lat) of this tile.
    *
    * NOTE: This repeats the first corner at the end of the Seq.
    * @param os Overscan amount in pixels
    */
  def corners(os: Int): Seq[(Double, Double)] =
    Seq(west(os) -> north(os),
        east(os) -> north(os),
        east(os) -> south(os),
        west(os) -> south(os),
        west(os) -> north(os))
}

object QuadTile {
  /** The point (x, y) in tile (256x256) space.
    *
    * @param coordinate (MUTATED) the lat and lon of the point.
    */
  case class TileFilter(tile: QuadTile, flip: Boolean) extends CoordinateFilter {
    def filter(c: Coordinate): Unit = {
      val mapped = tile.px(c, flip)
      c.setCoordinate(mapped)
    }
  }
}
