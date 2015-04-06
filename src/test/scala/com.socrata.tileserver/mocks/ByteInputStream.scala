package com.socrata.tileserver.mocks

import java.io.{ByteArrayInputStream, InputStream}

import com.socrata.http.common.util.Acknowledgeable

case class ByteInputStream(bytes: Array[Byte]) extends InputStream with Acknowledgeable {
  val underlying = new ByteArrayInputStream(bytes)

  override def acknowledge(): Unit = ()
  override def read(): Int = underlying.read
}
