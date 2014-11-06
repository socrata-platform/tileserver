import implicits.EnrichedInt

case class QuadTile(rawX: Int, rawY: Int, z: Int) {
  private val MaxLen = 20037508.34 // Spherical Mercator
  private val (xu, yu) = (MaxLen, MaxLen)

  val depth: Int = 2 ** z
  val x: Int = rawX % depth
  val y: Int = rawY % depth

  val west = -xu * (1 - (2 ** (1 - z)) * x)
  val north = +yu * (1 - (2 ** (1 - z)) * y)
  val east = west + xu / (2 ** (z - 1))
  val south = north - yu / (2 ** (z - 1))

  def withinBox(pointColumn: String): String = {
    s"within_box($pointColumn, $west, $north, $east, $south)"
  }
}
