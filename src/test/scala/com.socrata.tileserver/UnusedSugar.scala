package com.socrata.tileserver

import java.io.{ByteArrayInputStream, InputStream}
import scala.language.implicitConversions

import com.socrata.testcommon.UnusedSugarCommon
import com.socrata.testcommon.UnusedSugarCommon._
import com.socrata.thirdparty.geojson.FeatureJson

import util.{RenderProvider, GeoProvider}

trait UnusedSugar extends UnusedSugarCommon {
  implicit def unusedToRenderer(u: UnusedValue): RenderProvider =
    RenderProvider(Unused, Unused)

  implicit def unusedToProvider(u: UnusedValue): GeoProvider = GeoProvider(Unused)

  implicit def unusedToQuadTile(u: UnusedValue): util.QuadTile =
    new util.QuadTile(0, 0, 0) {
      override def px(lon: Double, lat: Double, flip: Boolean): (Int, Int) = (lon.toInt, lat.toInt)
    }

  implicit def unusedToRequestInfo(u: UnusedValue): util.RequestInfo =
    util.RequestInfo(Unused, Unused, Unused, Unused, Unused, None)
}

object UnusedSugar extends UnusedSugar
