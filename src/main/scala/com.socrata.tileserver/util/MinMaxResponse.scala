package com.socrata.tileserver
package util

import java.io.{ByteArrayInputStream, DataInputStream}
import java.nio.charset.StandardCharsets._

import com.rojoma.json.v3.codec.{DecodeError, JsonCodec, JsonDecode}
import com.rojoma.json.v3.codec.JsonEncode.toJValue
import com.rojoma.json.v3.io.JsonReader
import com.rojoma.json.v3.util.{AutomaticJsonCodecBuilder, JsonUtil}
import com.rojoma.simplearm.v2.ResourceScope
import org.apache.commons.io.IOUtils


import com.socrata.http.client.{Response, ResponseInfo}
import com.socrata.soql.{SoQLPackIterator, SoQLGeoRow}


case class MinMax(min:String, max:String)
object MinMax {
  implicit val jCodec = AutomaticJsonCodecBuilder[MinMax]
}


trait MinMaxResponse extends ResponseInfo {
  override def resultCode: Int
  override def headers(name: String): Array[String]
  override def headerNames: Set[String]

  /** The resource scope to use when processing the response. */
  protected def resourceScope: ResourceScope

  /** The (binary) payload from the underlying response. */
  def payload: Array[Byte]

  def min: String = {
    val json = JsonReader.fromString(new String(payload, UTF_8))
    JsonDecode.fromJValue[Seq[MinMax]](json) match {
      case Right(minMaxArray) =>
        minMaxArray.head.min
      case Left(e) => throw new Exception ("unparseable json" + e.getMessage)
    }
  }

  //this duplication sucks but it's 2 am and how do you even scala
  def max: String = {
    val json = JsonReader.fromString(new String(payload, UTF_8))
    JsonDecode.fromJValue[Seq[MinMax]](json) match {
      case Right(minMaxArray) =>
        minMaxArray.head.max
      case Left(e) => throw new Exception ("unparseable json" + e.getMessage)
    }
  }
}

object MinMaxResponse {
  /** Create a `MinMaxResponse` from an underlying response.
    *
    * @param underlying the underlying response.
    * @param rs the resource scope to use when processing the response.
    */
  def apply(underlying: Response, rs: ResourceScope): MinMaxResponse =
    new MinMaxResponseImpl(underlying, rs)

  class MinMaxResponseImpl private[MinMaxResponse] (underlying: Response, rs: ResourceScope)
    extends MinMaxResponse {
    protected val resourceScope = rs

    val payload: Array[Byte] = {
      val is = underlying.inputStream()
      if (is != null) IOUtils.toByteArray(is) else Array.empty // scalastyle:ignore
    }

    val resultCode = underlying.resultCode
    def headers(name: String): Array[String] = underlying.headers(name)
    val headerNames: Set[String] = underlying.headerNames
  }
}
