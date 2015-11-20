package com.socrata.test
package http

import javax.servlet.http.HttpServletResponse
import scala.collection.JavaConverters._

import com.socrata.http.server.HttpResponse

import ResponseSugar._

trait ResponseSugar {
  def unpackResponse(serverResp: HttpResponse): UnpackedResponse = {
    val os = new mocks.ByteArrayServletOutputStream
    val resp = os.responseFor
    serverResp(resp) // Mutates `resp`
    resp.freeze()    // Now we can pretend this is a reasonable class!
    new UnpackedResponse(resp, os)
  }
}

object ResponseSugar extends ResponseSugar {
  class Body(stream: mocks.ByteArrayServletOutputStream) {
    val toByteArray: Array[Byte] = stream.getBytes
    override lazy val toString: String = stream.getString
    lazy val toLowStr: String = stream.getLowStr
    lazy val toLowerCaseString: String = stream.getLowStr
  }

  class UnpackedResponse(val underlying: HttpServletResponse,
                         stream: mocks.ByteArrayServletOutputStream) {
    val status: Int = underlying.getStatus
    lazy val statusCode: Int = status
    lazy val contentType: String = underlying.getContentType
    lazy val body: Body = new Body(stream)
    lazy val headers: Map[String, String] = allHeaders.map { case (key, value) =>
      key -> allHeaders(key).head
    }

    lazy val allHeaders: Map[String, Seq[String]] = {
      val keys = underlying.getHeaderNames().asScala
      keys.map { key =>
        key -> underlying.getHeaders(key).asScala.toSeq
      }.toMap
    }
  }
}
