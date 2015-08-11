package com.socrata.tileserver
package mocks

import java.io.{ByteArrayOutputStream, DataOutputStream}

import com.vividsolutions.jts.io.WKBWriter
import org.velvia.MsgPack
import com.vividsolutions.jts.geom.{Coordinate, GeometryFactory}

import com.socrata.soql.types._

import MsgPackResponse._
import gen.Points.PointLike
import util.TileEncoder.Feature

class MsgPackResponse(header: Map[String, Any] = EmptyHeader,
                      rows: Seq[Seq[Any]] = Seq.empty,
                      junk: Option[Array[Byte]] = None)
    extends BinaryResponse(buildPayload(header, rows) ++ junk.getOrElse(Array[Byte]())) {

  def this(args: (Map[String, Any], Seq[Seq[Any]], Option[Array[Byte]])) {
    this(args._1, args._2, args._3)
  }

  def this(args: (Map[String, Any], Seq[Seq[Any]])) {
    this(args._1, args._2, None)
  }
}

object MsgPackResponse {
  val GeoIndexKey = "geometry_index"
  val EmptyHeader = Map("geometry_index" -> 0, "schema" -> Seq.empty)
  val GeomFactory = new GeometryFactory()

  def apply(header: Map[String, Any] = EmptyHeader,
            rows: Seq[Seq[Any]] = Seq.empty,
            junk: Option[Array[Byte]] = None): MsgPackResponse =
    new MsgPackResponse(header, rows, junk)

  def apply(points: Seq[PointLike], junk: Array[Byte]): MsgPackResponse =
    apply(points, Map[String, String](), Some(junk))

  def apply(point: Seq[PointLike]): MsgPackResponse =
    apply(point, Map[String, String]())

  def apply(points: Seq[PointLike], attrs: Map[String, String]): MsgPackResponse =
    apply(points, attrs, None)

  def apply(points: Seq[PointLike],
            attrs: Map[String, String],
            junk: Option[Array[Byte]]): MsgPackResponse = {
    val (header, rows) = buildMessage(points.map(pt =>
                                        pt.x.toDouble -> pt.y.toDouble), attrs)
    apply(header, rows, junk)
  }

  def apply(geoIndex: Int): MsgPackResponse =
    new MsgPackResponse(headerMap(geoIndex))

  def buildMessage(points: Seq[(Double, Double)],
                   attrs: Map[String, String]): (Map[String, Any], Seq[Seq[Any]]) = {
    val writer = new WKBWriter()

    val rows = points.map { pt =>
      val (x, y) = pt
      val point = GeomFactory.createPoint(new Coordinate(x, y))
      Seq(writer.write(point)) ++ attrs.values
    }

    val extraCols = attrs.keys.map(_ -> SoQLText)
    val header = headerMap(0, extraCols)

    (header, rows)
  }


  def buildPayload(header: Map[String, Any],
                   rows: Seq[Seq[Any]]): Array[Byte] = {
    val baos = new ByteArrayOutputStream
    val dos = new DataOutputStream(baos)
    MsgPack.pack(header, dos)
    rows.foreach(MsgPack.pack(_, dos))
    dos.flush()
    baos.toByteArray
  }

  def headerMap(geoIndex: Int,
                extraCols: Iterable[(String, SoQLType)] = Iterable.empty): Map[String, Any] = {
    assert(geoIndex <= 0)
    val schema: Seq[Map[String, String]] = if (geoIndex < 0) Nil else {
      (Seq("geo" -> SoQLPoint) ++ extraCols).map { case (col, typ) =>
        Map("c" -> col, "t" -> typ.toString)
      }
    }

    Map(GeoIndexKey -> geoIndex, "schema" -> schema)
  }
}
