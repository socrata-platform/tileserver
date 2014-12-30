package com.socrata.tileserver.util

import java.io.{ByteArrayInputStream, InputStream}

import com.socrata.http.common.util.Acknowledgeable

case class StringInputStream(s: String) extends InputStream with Acknowledgeable {
  val underlying = new ByteArrayInputStream(s.getBytes)

  override def acknowledge(): Unit = ()
  override def read(): Int = underlying.read
}
