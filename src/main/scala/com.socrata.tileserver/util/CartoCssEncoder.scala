package com.socrata.tileserver
package util

import java.awt.Color
import java.io.{ByteArrayInputStream, InputStream}
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util

import com.rojoma.json.v3.ast
import com.rojoma.json.v3.ast.JNumber
import com.rojoma.json.v3.codec.JsonDecode
import com.rojoma.json.v3.util.JsonUtil
import com.rojoma.simplearm.v2.ResourceScope
import com.socrata.tileserver.util.TileEncoder.Feature
import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.IOUtils
import org.slf4j.{Logger, LoggerFactory}
import org.velvia.MsgPack

import com.socrata.http.client.{HttpClient, RequestBuilder, Response}

import exceptions.FailedRenderException

case class CartoCssEncoder(info: RequestInfo) {
  def buildColor(value: Float) = {
    val rgbMin = Color.decode(info.rangedColorMin.get)
    val rgbMax = Color.decode(info.rangedColorMax.get)
    val percentValue = Math.abs((value - info.min.get)/info.max.get)

    val red = (rgbMin.getRed + (rgbMax.getRed - rgbMin.getRed) * percentValue)
    val blue = (rgbMin.getBlue + (rgbMax.getBlue - rgbMin.getBlue) * percentValue)
    val green = (rgbMin.getGreen + (rgbMax.getGreen - rgbMin.getGreen) * percentValue)

    val rgbValue = new Color(red.toInt, blue.toInt, green.toInt)
    "#%02x%02x%02x".format(red.toInt, green.toInt, blue.toInt)
  }

  def buildRangeCartoCSS: String = {
    val initialSliceRange = (info.max.get - info.min.get) / 20

    def buildSlices(sliceRange: Float, str: String, accumulator: Float): String = {
      if (accumulator  >= info.max.get) {
        str
      }
      else {
        val low = accumulator
        val high = Math.min(info.max.get, accumulator + sliceRange)

        val newString = s"[${info.columnName.get} >= $low][${info.columnName.get} <= $high]{polygon-fill: ${buildColor((high + low)/2)}}"
        buildSlices(sliceRange, str + newString, accumulator + sliceRange)
      }
    }
    buildSlices(initialSliceRange, "", info.min.get)
  }

  def buildCartoCSS: String = {
    val rangeCartoCSS = buildRangeCartoCSS
    val cartoCss = s"#multipolygon, #polygon { $rangeCartoCSS }".replaceAll("\\s+", "")
    (cartoCss + info.style.get).replaceAll("\\s+", "")
  }


  def buildGranularCartoCSS(features: Set[Feature]): String = {
    // make this look prettier
    val cartoCssString = features.flatMap {
      case (geometry, attributes) =>
        info.columnName match {
          case Some(colName) =>
            attributes.get("properties") match {
              case Some(properties) =>
                properties.dyn(colName).? match {
                  case Right(property) =>
                    JsonDecode.fromJValue[JNumber](property) match {
                      case Right(x) =>
                        Some(s"[$colName = $property] { polygon-fill : ${buildColor(x.toBigDecimal.toFloat)}; }")
                      case Left(e) => None
                    } //this is terrible but whatever

                  case Left(e) => None
                }
              case None => None
            }
          case None => None
        }
    } mkString "\n"

    val cartoCss = s"#multipolygon, #polygon { $cartoCssString }".replaceAll("\\s+","")
    (cartoCss + info.style.get).replaceAll("\\s+","")
  }
}
