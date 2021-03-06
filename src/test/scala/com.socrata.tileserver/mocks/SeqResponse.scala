package com.socrata.tileserver
package mocks

import java.io.InputStream
import javax.activation.MimeType

import com.rojoma.json.v3.ast.{JString, JValue}
import com.vividsolutions.jts.geom.Point

import com.socrata.http.client.Response
import com.socrata.http.common.util.Acknowledgeable
import com.socrata.http.server.responses._
import com.socrata.thirdparty.geojson.GeoJson.codec.encode
import com.socrata.thirdparty.geojson.{FeatureCollectionJson, FeatureJson}

import SeqResponse._

class SeqResponse(seq: Seq[FeatureJson]) extends MsgPackResponse(buildMessage(seq)) {
  override val resultCode = OK.statusCode

  override def toString: String =
    encode(FeatureCollectionJson(seq)).toString.replaceAll("\\s*", "")
}

object SeqResponse {
  def apply(seq: Seq[FeatureJson]): SeqResponse = new SeqResponse(seq)
  def apply(json: FeatureJson): SeqResponse = new SeqResponse(Seq(json))

  // NOTE: Discards properties.
  def buildMessage(feat: Seq[FeatureJson]): (Map[String, Any], Seq[Seq[Any]]) = {
    val points = feat.collect {
      case f @ FeatureJson(_, _: Point, _) =>
        val p: Point = f.geometry.asInstanceOf[Point]
        (p.getX, p.getY)
    }

    MsgPackResponse.buildMessage(points, Map.empty)
  }
}
