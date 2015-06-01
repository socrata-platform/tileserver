package com.socrata.tileserver
package util

import com.vividsolutions.jts.geom.Coordinate

import CoordinateMapper.Size

class QuadTileTest extends TestBase {
  test("Tile includes point on east edge") {
    // scalastyle:off magic.number
    val tile = new QuadTile(330, 800, 11)
    val (lon, lat) = (-121.816769, 36.579349)
    tile.east must be > lon
    tile.west must be < lon
    tile.north must be < lat
    tile.south must be > lat

    tile.px(lon, lat) must equal (Some((255, 34)))
    // scalastyle:on magic.number
  }

  test("Left tile's east edge must touch right tile's west edge") {
    // scalastyle:off magic.number
    val left = new QuadTile(330, 800, 11)
    val right = new QuadTile(331, 800, 11)

    left.east must equal (right.west)
    // scalastyle:on magic.number
  }

  test("Upper tile's south edge must touch lower tile's north edge") {
    // scalastyle:off magic.number
    val upper = new QuadTile(330, 800, 11)
    val lower = new QuadTile(330, 799, 11)

    upper.south must equal (lower.north)
    // scalastyle:on magic.number
  }

  test("tilePx(lon, lat) maps correctly to pixels") {
    // scalastyle:off magic.number
    val tile14 = QuadTile(4207, 6101, 14)
    tile14.px(-87.539383, 41.681059) must equal (Some((252, 129)))
    tile14.px(-87.545220, 41.683555) must equal (Some((184, 90)))
    tile14.px(-87.541167, 41.688135) must equal (Some((231, 19)))
    tile14.px(-87.559501, 41.682697) must equal (Some((18, 103)))
    tile14.px(-87.557169, 41.688140) must equal (Some((45, 18)))

    val tile5 = QuadTile(8, 11, 5)
    tile5.px(-87.676410, 41.776204) must equal (Some((53, 232)))
    tile5.px(-87.560088, 41.753145) must equal (Some((56, 233)))
    tile5.px(-87.667603, 42.011423) must equal (Some((53, 225)))
    // scalastyle:on magic.number
  }

  test("tile.px(lon, lat) maps points into pixel space") {
    import gen.Points._ // scalastyle:ignore
    import gen.QuadTiles._ // scalastyle:ignore

    forAll { (tile: QuadTile, pt: ValidPoint) =>
      val (lon, lat) = pt.onto(tile)
      val (tx, ty) = tile.mapper.px(lon, lat)

      tile.px(lon, lat) must equal (Some((tx % Size, ty % Size)))
    }
  }

  test("tile.px(Coordinate(x, y)) returns the same values as tile.px(x, y)") {
    import gen.Points._ // scalastyle:ignore
    import gen.QuadTiles._ // scalastyle:ignore

    forAll { (tile: QuadTile, pt: ValidPoint) =>
      val (lon, lat) = pt.onto(tile)

      val (tx, ty) = tile.px(lon, lat).get
      new Coordinate(tx, ty) must equal (tile.px(new Coordinate(lon, lat)).get)
    }
  }

  test("tile.px(lon, lat) does not map points that aren't on the tile.") {
    import gen.Points._ // scalastyle:ignore
    import gen.QuadTiles._ // scalastyle:ignore

    forAll { (tile: QuadTile, pt: InvalidPoint) =>
      val (lon, lat) = pt.onto(tile)

      tile.px(lon, lat) must be (None)
    }
  }

  test("North and East edges are excluded)") {
    import gen.QuadTiles._ // scalastyle:ignore

    forAll { tile: QuadTile =>
      tile.px(tile.west, tile.north) must be (None)
      tile.px(tile.east, tile.north) must be (None)
      tile.px(tile.east, tile.south) must be (None)
    }
  }

  test("Points next to the North and East edges are included") {
    import gen.Points._ // scalastyle:ignore
    import gen.QuadTiles._ // scalastyle:ignore

    val c = Size - 1

    forAll { tile: QuadTile =>
      val mapper = tile.mapper
      val (west, south) = (tile.west, tile.south)
      val offset = Point(c, c)
      val (east, north) = offset.onto(tile)

      tile.px(west, north) must be (Some((0, c)))
      tile.px(east, north) must be (Some((c, c)))
      tile.px(east, south) must be (Some((c, 0)))
      tile.px(west, south) must be (Some((0, 0)))
    }
  }
}
