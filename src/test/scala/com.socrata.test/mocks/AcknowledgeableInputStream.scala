package com.socrata.test.mocks

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.charset.{Charset, StandardCharsets}

import com.socrata.http.common.util.Acknowledgeable

/** Will return user provided data to its consumers. */
class AcknowledgeableInputStream(payload: Array[Byte])
    extends InputStream
    with Acknowledgeable {
  def this(payload: String, charset: Charset) = this(payload.getBytes(charset))
  def this(payload: String) = this(payload, StandardCharsets.UTF_8)
  def this() = this(Array.empty[Byte])

  val underlying = new ByteArrayInputStream(payload)

  override def acknowledge(): Unit = ()
  override def read(): Int = underlying.read
}
