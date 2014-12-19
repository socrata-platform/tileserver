package com.socrata.tileserver.util

import javax.activation.MimeType

import com.rojoma.json.v3.ast.JValue
import com.rojoma.json.v3.conversions._
import com.socrata.thirdparty.geojson.GeoJson.codec.encode
import com.socrata.thirdparty.geojson.{FeatureCollectionJson, FeatureJson}

import com.socrata.http.client.Response

class SeqResponse(seq: Seq[FeatureJson]) extends EmptyResponse {
  override def jValue(ct: Option[MimeType] => Boolean = EmptyResponse.AnyMimeType,
                      max: Long = 0): JValue = encode(FeatureCollectionJson(seq)).toV3
}

object SeqResponse {
  def apply(seq: Seq[FeatureJson]): SeqResponse = new SeqResponse(seq)
}
