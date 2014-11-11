import CoordinateMapper._
import com.vividsolutions.jts.geom.Coordinate
import scala.math.{Pi, atan, exp, log, min, max, sin}

case class CoordinateMapper(val zoom: Int) {
  val SizeZoomed: Int = Size * (1 << zoom)

  def tmsCoordinates(x: Int, y: Int): (Int, Int) = (x, (1 << zoom) - (y + 1))

  /** Returns the longitude corresponding to "x". */
  def lon(x: Int): Double = (x - SizeZoomed / 2) / (SizeZoomed / 360.0)

  /** Returns the latitude corresponding to "y".
    * y should be in TMS Coordinates.
    */
  def lat(y: Int): Double = {
    val g = (Pi * (-2 * y + SizeZoomed)) / SizeZoomed
    val r2d = 180 / Pi

    -1 * r2d * (2 * atan(exp(g)) - 0.5 * Pi)
  }

  def px(c: Coordinate): Coordinate = {
    val (x, y) = px(c.x, c.y)
    new Coordinate(x, y)
  }

  /** Returns the pixel (x and y) corresponding to "lon" and "lat" */
  def px(lon: Double, lat: Double): (Int, Int) = {
    val d2r = Pi / 180
    val bc = SizeZoomed / 360
    val cc = SizeZoomed / (2 * Pi)
    val d  = SizeZoomed / 2;

    val f = min(max(sin(d2r * lat), -0.9999), 0.9999);
    val x = (d + lon * bc).round;
    val y = (d + 0.5 * log((1 + f) / (1 - f)) * (-cc)).round;

    // (if (x <= SizeZoomed) x.toInt else SizeZoomed,
    //  if (y <= SizeZoomed) y.toInt else SizeZoomed)

    ((x / (1 << 14)).toInt, (y / (1 << 14)).toInt)
  }
}

object CoordinateMapper {
  val Size: Int = 256
}
