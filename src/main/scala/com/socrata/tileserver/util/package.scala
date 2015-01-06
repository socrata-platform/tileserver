package com.socrata.tileserver

import scala.collection.JavaConverters._

import no.ecc.vectortile.VectorTileDecoder
import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.IOUtils

import com.socrata.http.client.Response
import com.socrata.http.client.Response.ContentP
import com.socrata.http.server.HttpResponse
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._

package object util {
  type Encoder = Response => Option[Array[Byte]]
  type Extension = (Encoder, Response) => HttpResponse

  val JsonP: ContentP = _ map { t =>
      t.getBaseType.startsWith("application/") && t.getBaseType.endsWith("json")
    } getOrElse false


  val InvalidJson = InternalServerError ~>
    Content("application/json", """{"message":"Invalid geo-json returned from underlying service."}""")

  val DefaultResponse = OK ~> Header("Access-Control-Allow-Origin", "*")

  val PbfExt: Extension = (encoder, resp) => encoder(resp) map { bytes: Array[Byte] =>
    DefaultResponse ~>
      ContentType("application/octet-stream") ~>
      ContentBytes(bytes)
  } getOrElse InvalidJson

  val B64PbfExt: Extension = (encoder, resp) => encoder(resp) map { bytes: Array[Byte] =>
    DefaultResponse ~>
      Content("text/plain", Base64.encodeBase64String(bytes))
  } getOrElse InvalidJson

  val TxtExt: Extension = (encoder, resp) => encoder(resp) map {
    bytes: Array[Byte] => {
      // $COVERAGE-OFF$ Disabled because the ".txt" extension is purely for debugging.
      val decoder = new VectorTileDecoder()
      decoder.decode(bytes)

      val features = decoder.getFeatures("main").asScala map { f =>
        s"geometry: ${f.getGeometry.toString}  \tattributes: ${f.getAttributes}"
      }
      // $COVERAGE-ON$

      DefaultResponse ~>
        Content("text/plain", features.mkString("\n"))
    }
  } getOrElse InvalidJson

  val JsonExt: Extension = (unused, resp) => {
    DefaultResponse ~> Json(resp.jValue(JsonP))
  }

  val Extensions: Map[String, Extension] = Map("pbf" -> PbfExt,
                                               "bpbf" -> B64PbfExt,
                                               "txt" -> TxtExt,
                                               "json" -> JsonExt)
}
