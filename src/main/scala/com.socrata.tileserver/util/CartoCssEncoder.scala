package com.socrata.tileserver
package util

import scala.annotation.tailrec
import java.awt.Color // scalastyle:ignore

import org.slf4j.{Logger, LoggerFactory}

import com.socrata.tileserver.util.RequestInfo.{MinMaxInfo, RangedColumnInfo}

case class CartoCssEncoder(info: RequestInfo, style: String) {
  def buildColor(value: Float,
                 rangedColumn: RangedColumnInfo,
                 minMax: MinMaxInfo): String = {
    val rgbMin = Color.decode(rangedColumn.minColor)
    val rgbMax = Color.decode(rangedColumn.maxColor)
    val percentValue = Math.abs((value - minMax.minValue) / minMax.maxValue)

    val red = rgbMin.getRed + (rgbMax.getRed - rgbMin.getRed) * percentValue
    val blue = rgbMin.getBlue + (rgbMax.getBlue - rgbMin.getBlue) * percentValue
    val green = rgbMin.getGreen + (rgbMax.getGreen - rgbMin.getGreen) * percentValue

    val rgbValue = new Color(red.toInt, blue.toInt, green.toInt)
    "#%02x%02x%02x".format(red.toInt, green.toInt, blue.toInt)
  }

  private def buildRangeCartoCSS(rangedColumn: RangedColumnInfo, minMax: MinMaxInfo): String = {
    val initialSliceRange = (minMax.maxValue - minMax.maxValue) / 20

    @tailrec def buildSlices(sliceRange: Float, str: String, accumulator: Float): String = {
      if (accumulator >= minMax.maxValue) {
        str
      } else {
        val low = accumulator
        val high = Math.min(minMax.maxValue, accumulator + sliceRange)
        val rgbValue = buildColor((high + low) / 2, rangedColumn, minMax)
        val newString =
          s"[${rangedColumn.name} >= $low][${rangedColumn.name} <= $high]{polygon-fill: $rgbValue}"

        buildSlices(sliceRange, str + newString, accumulator + sliceRange)
      }
    }

    val defaultMinCss = s"[${rangedColumn.name} <= ${rangedColumn.minColor}]{polygon-fill: ${minMax.minValue}}"
    val defaultMaxCss = s"[${rangedColumn.name} >= ${rangedColumn.minColor}]{polygon-fill: ${minMax.maxValue}}"

    buildSlices(initialSliceRange, s"$defaultMinCss $defaultMaxCss", minMax.minValue)
  }

  private def buildCartoCSS(style: String, rangedColumn: RangedColumnInfo, minMaxValues: MinMaxInfo): String = {
    val rangeCartoCSS = buildRangeCartoCSS(rangedColumn, minMaxValues)
    val cartoCss = s"#multipolygon, #polygon { $rangeCartoCSS }"

    (cartoCss + style).replaceAll("\\s+", "")
  }

  def cartoCss: String = {
    (info.rangedColumn, info.minMaxValues) match {
      case (Some(rangedColumn), Some(minMax)) => buildCartoCSS(style, rangedColumn, minMax)
      case _ => style
    }
  }
}
