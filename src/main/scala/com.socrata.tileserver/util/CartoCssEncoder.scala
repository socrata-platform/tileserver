package com.socrata.tileserver
package util

import java.awt.Color

import com.socrata.tileserver.util.RequestInfo.{MinMaxInfo, RangedColumnInfo}

// scalastyle:ignore

import com.rojoma.json.v3.$minusimpl.dynamic.DynamicPathType.str

import scala.annotation.tailrec

import org.slf4j.{Logger, LoggerFactory}


case class CartoCssEncoder(info: RequestInfo) {
  def buildColor(value: Float): String = {
    val rgbMin = Color.decode(info.rangedColumn.get.minColor)
    val rgbMax = Color.decode(info.rangedColumn.get.maxColor)
    val percentValue = Math.abs((value - info.minMaxValues.get.minValue)/info.minMaxValues.get.maxValue)

    val red = rgbMin.getRed + (rgbMax.getRed - rgbMin.getRed) * percentValue
    val blue = rgbMin.getBlue + (rgbMax.getBlue - rgbMin.getBlue) * percentValue
    val green = rgbMin.getGreen + (rgbMax.getGreen - rgbMin.getGreen) * percentValue

    val rgbValue = new Color(red.toInt, blue.toInt, green.toInt)
    "#%02x%02x%02x".format(red.toInt, green.toInt, blue.toInt)
  }

  private def buildRangeCartoCSS: String = {
    val initialSliceRange = (info.minMaxValues.get.maxValue - info.minMaxValues.get.maxValue) / 20

    @tailrec def buildSlices(sliceRange: Float, str: String, accumulator: Float): String = {
      if (accumulator >= info.minMaxValues.get.maxValue) {
        str
      } else {
        val low = accumulator
        val high = Math.min(info.minMaxValues.get.maxValue, accumulator + sliceRange)
        val rgbValue = buildColor((high + low) / 2)
        val newString = s"[${info.rangedColumn.get.name} >= $low][${info.rangedColumn.get.name} <= $high]{polygon-fill: $rgbValue}"
        buildSlices(sliceRange, str + newString, accumulator + sliceRange)
      }
    }
    val defaultMinCss = s"[${info.rangedColumn.get.name} <= ${info.minMaxValues.get.minValue}]{polygon-fill: ${info.rangedColumn.get.minColor}}"
    val defaultMaxCss = s"[${info.rangedColumn.get.name} >= ${info.minMaxValues.get.maxValue}]{polygon-fill: ${info.rangedColumn.get.maxColor}}"

    buildSlices(initialSliceRange, s"$defaultMinCss $defaultMaxCss", info.minMaxValues.get.minValue)
  }

  private def buildCartoCSS: String = {
    val rangeCartoCSS = buildRangeCartoCSS
    val cartoCss = s"#multipolygon, #polygon { $rangeCartoCSS }"
    (cartoCss + info.style.get).replaceAll("\\s+", "")
  }

  def cartoCss: String = {
    (info.rangedColumn, info.minMaxValues) match {
      case (Some(_), Some(_)) => buildCartoCSS
      case _ => info.style.get
    }
  }
}
