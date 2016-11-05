package com.socrata.tileserver
package util

import java.awt.Color // scalastyle:ignore

import com.rojoma.json.v3.$minusimpl.dynamic.DynamicPathType.str

import scala.annotation.tailrec

import org.slf4j.{Logger, LoggerFactory}


case class CartoCssEncoder(info: RequestInfo) {
  def buildColor(value: Float): String = {
    val rgbMin = Color.decode(info.rangedColorMin.get)
    val rgbMax = Color.decode(info.rangedColorMax.get)
    val percentValue = Math.abs((value - info.min.get)/info.max.get)

    val red = rgbMin.getRed + (rgbMax.getRed - rgbMin.getRed) * percentValue
    val blue = rgbMin.getBlue + (rgbMax.getBlue - rgbMin.getBlue) * percentValue
    val green = rgbMin.getGreen + (rgbMax.getGreen - rgbMin.getGreen) * percentValue

    val rgbValue = new Color(red.toInt, blue.toInt, green.toInt)
    "#%02x%02x%02x".format(red.toInt, green.toInt, blue.toInt)
  }

  private def buildRangeCartoCSS: String = {
    val initialSliceRange = (info.max.get - info.min.get) / 20

    @tailrec def buildSlices(sliceRange: Float, str: String, accumulator: Float): String = {
      if (accumulator >= info.max.get) {
        str
      } else {
        val low = accumulator
        val high = Math.min(info.max.get, accumulator + sliceRange)
        val rgbValue = buildColor((high + low) / 2)
        val newString = s"[${info.columnName.get} >= $low][${info.columnName.get} <= $high]{polygon-fill: $rgbValue}"
        buildSlices(sliceRange, str + newString, accumulator + sliceRange)
      }
    }
    val defaultMinCss = s"[${info.columnName.get} <= ${info.min.get}]{polygon-fill: ${info.rangedColorMin.get}}"
    val defaultMaxCss = s"[${info.columnName.get} >= ${info.max.get}]{polygon-fill: ${info.rangedColorMax.get}}"

    buildSlices(initialSliceRange, s"$defaultMinCss $defaultMaxCss", info.min.get)
  }

  private def buildCartoCSS: String = {
    val rangeCartoCSS = buildRangeCartoCSS
    val cartoCss = s"#multipolygon, #polygon { $rangeCartoCSS }"
    (cartoCss + info.style.get).replaceAll("\\s+", "")
  }

  def cartoCss: String = {
    (info.min, info.max) match {
      case (Some(_), Some(_)) => buildCartoCSS
      case _ => info.style.get
    }
  }
}
