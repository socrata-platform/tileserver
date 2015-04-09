package com.socrata.tileserver.mocks

import java.io.{InputStream, ByteArrayOutputStream, DataOutputStream}
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}
import org.velvia.MsgPack

import com.socrata.http.common.util.Acknowledgeable

class BinaryResponse(val payload: Array[Byte],
                     override val resultCode: Int = ScOk) extends EmptyResponse("application/octet-stream") {
  override def inputStream(maxBetween: Long): InputStream with Acknowledgeable =
    ByteInputStream(payload)
}

object BinaryResponse {
  def apply(payload: Array[Byte], resultCode: Int = ScOk): BinaryResponse =
    new BinaryResponse(payload, resultCode)
  // Below is for quickly generating binary SoQLPack
  def apply(header: Map[String, Any], rows: Seq[Seq[Any]],
            junk: Option[Array[Byte]]): BinaryResponse = {
    val baos = new ByteArrayOutputStream
    val dos = new DataOutputStream(baos)
    MsgPack.pack(header, dos)
    rows.foreach(MsgPack.pack(_, dos))
    // Now write a junk row to test parsing errors
    junk foreach { junkBytes => MsgPack.pack(Seq(junkBytes), dos) }
    dos.flush()
    new BinaryResponse(baos.toByteArray)
  }

  def apply(header: Map[String, Any], rows: Seq[Seq[Any]]): BinaryResponse =
    apply(header, rows, None)
}
