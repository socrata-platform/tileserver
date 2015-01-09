package com.socrata.tileserver.mocks

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.charset.StandardCharsets.UTF_8

import com.rojoma.json.v3.io.{JsonReader, JsonReaderException}

import com.socrata.http.common.util.Acknowledgeable

case class StringInputStream(s: String) extends InputStream with Acknowledgeable {
  val underlying = new ByteArrayInputStream(sanitize(s).getBytes(UTF_8))

  override def acknowledge(): Unit = ()
  override def read(): Int = underlying.read

  def sanitize(input: String): String = {
    try {
      JsonReader.fromString(input).toString
    } catch {
      case _: JsonReaderException => "{}"
    }
  }
}
