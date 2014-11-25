package com.socrata.tileserver.util

import java.io.ByteArrayOutputStream
import javax.servlet.ServletOutputStream

class ByteArrayServletOutputStream extends ServletOutputStream {
  val underlying: ByteArrayOutputStream = new ByteArrayOutputStream

  def write(x: Int): Unit = underlying.write(x)
  def isReady(): Boolean = true
  def setWriteListener(x: javax.servlet.WriteListener): Unit = {}

  def getBytes: Array[Byte] = underlying.toByteArray
  def getString: String = underlying.toString
}
