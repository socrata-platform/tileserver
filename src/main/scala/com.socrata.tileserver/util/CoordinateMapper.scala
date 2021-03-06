package com.socrata.tileserver
package util

import scala.math.{Pi, atan, exp, log, min, max, sin, round}

import CoordinateMapper._

/** Map coordinates between lat/lon and pixels.
  *
  * Most of the mapping logic is ported from here:
  * https://github.com/mapbox/node-sphericalmercator
  *
  * @constructor Create an instance for this zoom level.
  * @param zoom The zoom level, lower numbers are zoomed out further.
  */
case class CoordinateMapper(val zoom: Int) {
  private val SizeZoomed: Int = Size * (1 << zoom)
  private val ZoomFactor: Float = (1 << zoom) * 1.0f

  private val d2r = Pi / 180.0
  private val r2d = 180 / Pi
  private val bc = SizeZoomed / 360.0
  private val cc = SizeZoomed / (2.0 * Pi)
  private val d  = SizeZoomed / 2.0

  /** (x, y) in TMS coordinates.
    *
    * @param x the x coordinate to be mapped.
    * @param y the y coordinate to be mapped.
    */
  def tmsCoordinates(x: Int, y: Int): (Int, Int) = (x, (1 << zoom) - (y + 1))

  /** The longitude for x.
    *
    * @param x the x coordinate in TMS coordinates.
    */
  def lon(x: Int): Double = (x - SizeZoomed / 2) / (SizeZoomed / 360.0)

  /** The latitude for y.
    *
    * @param y the y coordinate in TMS coordinates.
    */
  def lat(y: Int): Double = {
    val g = (Pi * (2 * -y + SizeZoomed)) / SizeZoomed

    -1 * r2d * (2 * atan(exp(g)) - 0.5 * Pi)
  }

  /** The pixel (x, y) corresponding to "lon" and "lat" */
  private[util] def px(lon: Double, lat: Double): (Int, Int) = {
    val f = min(max(sin(d2r * lat), -0.9999), 0.9999)

    val x = (d + lon * bc).round.toInt
    val y = (d + 0.5 * log((1.0 + f) / (1.0 - f)) * (-cc)).round.toInt

    (x, y)
  }
}

object CoordinateMapper {
  /** The size of the tile.
    *
    * NOTE: Underlying VectorTileEncoder only supports size = 256
    */
  val Size = 256
}
