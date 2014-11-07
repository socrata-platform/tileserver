import implicits._
import scala.math.{Pi, atan, exp}

case class QuadTile(rawX: Int, rawY: Int, zoom: Int) {
  val Size: Int = 256
  val SizeZoomed = Size * (2 ** zoom)

  val x = rawX
  val y = (2 ** zoom) - (rawY + 1)

  def lon(x: Int) = (x - SizeZoomed / 2) / (SizeZoomed / 360.0)

  def lat(y: Int) = {
    val g = (Pi * (-2 * y + SizeZoomed)) / SizeZoomed
    val r2d = (180 / Pi)

    r2d * (2 * atan(exp(g)) - 0.5 * Pi)
  }

  val north = lat(y * Size)
  val east  = lon(x * Size + Size)
  val south = lat(y * Size + Size)
  val west  = lon(x * Size)

  def withinBox(pointColumn: String): String = {
    s"within_box($pointColumn, $west, $north, $east, $south)"
  }
}

private object implicits {
  implicit class EnrichedInt(val i: Int) extends AnyVal {
    def ** (exp: Int): Int = scala.math.pow(i, exp).toInt
  }
}
