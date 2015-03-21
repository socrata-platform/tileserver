package com.socrata.tileserver.mocks

import java.io.InputStream
import javax.activation.MimeType
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}

import com.rojoma.json.v3.ast.JValue
import com.rojoma.json.v3.conversions._
import com.socrata.http.common.util.Acknowledgeable
import com.socrata.thirdparty.geojson.GeoJson.codec.encode
import com.socrata.thirdparty.geojson.{FeatureCollectionJson, FeatureJson}

import com.socrata.http.client.Response

class SeqResponse(seq: Seq[FeatureJson]) extends EmptyResponse {
  override val resultCode = ScOk

  override def toString: String =
    encode(FeatureCollectionJson(seq)).toString.replaceAll("\\s*", "")

  override def inputStream(maxBetween: Long): InputStream with Acknowledgeable =
    StringInputStream(toString)
}

object SeqResponse {
  def apply(seq: Seq[FeatureJson]): SeqResponse = new SeqResponse(seq)
}
