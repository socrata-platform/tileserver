package com.socrata.test
package mocks

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import javax.servlet.ServletOutputStream
import javax.servlet.http.HttpServletResponse

class ByteArrayServletOutputStream extends ServletOutputStream {
  val underlying: ByteArrayOutputStream = new ByteArrayOutputStream

  def write(x: Int): Unit = underlying.write(x)
  def isReady(): Boolean = true
  def setWriteListener(x: javax.servlet.WriteListener): Unit = {}

  lazy val getBytes: Array[Byte] = underlying.toByteArray
  def getString: String = new String(getBytes, UTF_8)
  // Shortened so it is the same length as "getString"
  def getLowStr: String = getString.toLowerCase

  val responseFor: OutputStreamServletResponse = new OutputStreamServletResponse(this)
}
