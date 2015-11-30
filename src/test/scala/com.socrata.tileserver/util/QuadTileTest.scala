package com.socrata.tileserver
package util

import com.vividsolutions.jts.geom.Coordinate

import CoordinateMapper.Size

// scalastyle:off import.grouping
class QuadTileTest extends TestBase {
  def flip(n: Int): Int = 255 - n

  test("Tile includes point on east edge") {
    val tile = new QuadTile(330, 800, 11)
    val (lon, lat) = (-121.816769, 36.579349)
    tile.east(0) must be > lon
    tile.west(0) must be < lon
    tile.north(0) must be < lat
    tile.south(0) must be > lat

    tile.px(lon, lat, false) must equal ((255, 34))
    tile.px(lon, lat, true) must equal ((255, flip(34)))
  }

  test("Left tile's east edge must touch right tile's west edge") {
    val left = new QuadTile(330, 800, 11)
    val right = new QuadTile(331, 800, 11)

    left.east(0) must equal (right.west(0))
  }

  test("Upper tile's south edge must touch lower tile's north edge") {
    val upper = new QuadTile(330, 800, 11)
    val lower = new QuadTile(330, 799, 11)

    upper.south(0) must equal (lower.north(0))
  }

  test("tilePx(lon, lat) maps correctly to pixels") {
    val tile14 = QuadTile(4207, 6101, 14)
    tile14.px(-87.539383, 41.681059, false) must equal ((252, 129))
    tile14.px(-87.545220, 41.683555, false) must equal ((184, 90))
    tile14.px(-87.541167, 41.688135, false) must equal ((231, 19))
    tile14.px(-87.559501, 41.682697, false) must equal ((18, 103))
    tile14.px(-87.557169, 41.688140, false) must equal ((45, 18))

    tile14.px(-87.539383, 41.681059, true) must equal ((252, flip(129)))
    tile14.px(-87.545220, 41.683555, true) must equal ((184, flip(90)))
    tile14.px(-87.541167, 41.688135, true) must equal ((231, flip(19)))
    tile14.px(-87.559501, 41.682697, true) must equal ((18, flip(103)))
    tile14.px(-87.557169, 41.688140, true) must equal ((45, flip(18)))

    val tile5 = QuadTile(8, 11, 5)
    tile5.px(-87.676410, 41.776204, false) must equal ((53, 232))
    tile5.px(-87.560088, 41.753145, false) must equal ((56, 233))
    tile5.px(-87.667603, 42.011423, false) must equal ((53, 225))

    tile5.px(-87.676410, 41.776204, true) must equal ((53, flip(232)))
    tile5.px(-87.560088, 41.753145, true) must equal ((56, flip(233)))
    tile5.px(-87.667603, 42.011423, true) must equal ((53, flip(225)))
  }

  test("tile.px(lon, lat) maps points into pixel space") {
    import gen.Points._
    import gen.QuadTiles._

    forAll { (tile: QuadTile, pt: ValidPoint) =>
      val (lon, lat) = pt.onto(tile)
      val (tx, ty) = tile.mapper.px(lon, lat)

      tile.px(lon, lat, false) must equal ((tx % Size, ty % Size))
      tile.px(lon, lat, true) must equal ((tx % Size, flip(ty % Size)))
    }
  }

  test("tile.px(Coordinate(x, y)) returns the same values as tile.px(x, y)") {
    import gen.Points._
    import gen.QuadTiles._

    forAll { (tile: QuadTile, pt: ValidPoint) =>
      val (lon, lat) = pt.onto(tile)

      val (tx, ty) = tile.px(lon, lat, false)
      new Coordinate(tx, ty) must equal (tile.px(new Coordinate(lon, lat), false))
      new Coordinate(tx, ty) must equal (tile.px(new Coordinate(lon, lat), false))

      val (txf, tyf) = tile.px(lon, lat, true)
      new Coordinate(txf, tyf) must equal (tile.px(new Coordinate(lon, lat), true))
      new Coordinate(txf, tyf) must equal (tile.px(new Coordinate(lon, lat), true))
    }
  }

  test("Points next to the North and East edges are included") {
    import gen.Points._
    import gen.QuadTiles._

    val c = Size - 1

    forAll { tile: QuadTile =>
      val mapper = tile.mapper
      val (west, south) = (tile.west(0), tile.south(0))
      val offset = Point(c, c)
      val (east, north) = offset.onto(tile)

      tile.px(west, north, false) must be ((0, c))
      tile.px(east, north, false) must be ((c, c))
      tile.px(east, south, false) must be ((c, 0))
      tile.px(west, south, false) must be ((0, 0))

      tile.px(west, north, true) must be ((0, flip(c)))
      tile.px(east, north, true) must be ((c, flip(c)))
      tile.px(east, south, true) must be ((c, flip(0)))
      tile.px(west, south, true) must be ((0, flip(0)))
    }
  }

  test("x and y are centered on the standard viewport") {
    import gen.QuadTiles._

    forAll { tile: QuadTile =>
      val QuadTile(rawX, rawY, zoom) = tile
      val wrap = 1 << zoom
      val wrappedTile = QuadTile(rawX + wrap, rawY + wrap, zoom)
      tile.x must equal (wrappedTile.x)
      tile.y must equal (wrappedTile.y)
    }
  }
}
