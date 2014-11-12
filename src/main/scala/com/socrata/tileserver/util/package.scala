package com.socrata.tileserver

import com.socrata.http.client.Response
import com.socrata.http.server.HttpResponse
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import no.ecc.vectortile.VectorTileDecoder
import org.apache.commons.io.IOUtils
import scala.collection.JavaConverters._

package object util {
  type Encoder = Response => Option[Array[Byte]]
  type Extension = (Encoder, Response) => HttpResponse

  val invalidJson = InternalServerError ~>
    ContentType("application/json") ~>
    Content("""{"message":"Invalid geo-json returned from underlying service."}""")

  val Pbf: Extension = (encoder, resp) => encoder(resp) map {
    bytes: Array[Byte] => {
      OK ~>
        ContentType("application/octet-stream") ~>
        Header("Access-Control-Allow-Origin", "*") ~>
        ContentBytes(bytes)
    }
  } getOrElse invalidJson

  val Txt: Extension = (encoder, resp) => encoder(resp) map {
    bytes: Array[Byte] => {
      val decoder = new VectorTileDecoder()
      decoder.decode(bytes)

      val features = decoder.getFeatures("main").asScala map { f =>
        s"geometry: ${f.getGeometry.toString}  \tattributes: ${f.getAttributes}"
      }

      OK ~>
        ContentType("text/plain") ~>
        Content(features.mkString("\n"))
    }
  } getOrElse invalidJson

  val Json: Extension = (unused, resp) => {
    OK ~>
      ContentType("application/json") ~>
      Header("Access-Control-Allow-Origin", "*") ~>
      Stream(IOUtils.copy(resp.inputStream(), _))
  }

  val Extensions: Map[String, Extension] = Map("pbf" -> Pbf,
                                               "txt" -> Txt,
                                               "json" -> Json)
}
