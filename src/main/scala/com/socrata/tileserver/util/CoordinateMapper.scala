package com.socrata.tileserver.util

import scala.math.{Pi, atan, exp, log, min, max, sin, round}

import com.vividsolutions.jts.geom.Coordinate

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

  /** Returns TMS coordinates.
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
    val r2d = 180 / Pi

    -1 * r2d * (2 * atan(exp(g)) - 0.5 * Pi)
  }

  def px(c: Coordinate): Coordinate = {
    val (x, y) = px(c.x, c.y)
    new Coordinate(x, y)
  }

  /** Returns the pixel (x and y) corresponding to "lon" and "lat" */
  def px(lon: Double, lat: Double): (Int, Int) = {
    val d2r = Pi / 180.0
    val bc = SizeZoomed / 360.0
    val cc = SizeZoomed / (2.0 * Pi)
    val d  = SizeZoomed / 2.0

    val f = min(max(sin(d2r * lat), -0.9999), 0.9999)
    val x = (d + lon * bc).round.toInt
    val y = (d + 0.5 * log((1.0 + f) / (1.0 - f)) * (-cc)).round.toInt

    (x % Size, y % Size)
  }
}

object CoordinateMapper {
  val Size: Int = 256
}
