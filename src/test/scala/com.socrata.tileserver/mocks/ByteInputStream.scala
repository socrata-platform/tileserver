package com.socrata.tileserver.mocks

import java.io.{ByteArrayInputStream, InputStream}

import com.socrata.http.common.util.Acknowledgeable

object ByteInputStream {
  def apply(bytes: Array[Byte]): InputStream with Acknowledgeable =
    new ByteArrayInputStream(bytes) with Acknowledgeable {
      override def acknowledge(): Unit = ()
    }
}
