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

  override def jValue(ct: Option[MimeType] => Boolean = EmptyResponse.AnyMimeType,
                      max: Long = 0): JValue =
    encode(FeatureCollectionJson(seq)).toV3

  override def toString: String = jValue().toString.replaceAll("\\s*", "")

  override def inputStream(maxBetween: Long): InputStream with Acknowledgeable =
    new AckByteArrayInputStream(toString.getBytes)
}

object SeqResponse {
  def apply(seq: Seq[FeatureJson]): SeqResponse = new SeqResponse(seq)
}