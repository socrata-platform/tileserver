package com.socrata.tileserver
package util

import java.io.{ByteArrayInputStream, DataInputStream}
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.{Failure, Success, Try}

import com.rojoma.json.v3.codec.JsonEncode.toJValue
import com.rojoma.simplearm.v2.ResourceScope
import com.vividsolutions.jts.geom.Geometry
import org.apache.commons.io.IOUtils
import org.slf4j.{Logger, LoggerFactory}
import org.velvia.InvalidMsgPackDataException

import com.socrata.http.client.{RequestBuilder, Response, StandardResponse}
import com.socrata.http.server.util.RequestId.{RequestId, ReqIdHeader}
import com.socrata.http.server.{HttpRequest, HttpResponse}
import com.socrata.soql.{SoQLPackIterator, SoQLGeoRow}
import com.socrata.thirdparty.curator.CuratedServiceClient
import com.socrata.thirdparty.geojson.FeatureJson

import GeoProvider._
import TileEncoder.Feature
import exceptions._

case class GeoProvider(client: CuratedServiceClient) {
  // TODO: Remove these forwarding methods, hide them behind a nice API.
  def unpackFeatures(rs: ResourceScope) = GeoProvider.unpackFeatures(rs)
  def rollup(tile: QuadTile,
             features: => Iterator[FeatureJson]) = GeoProvider.rollup(tile, features)

  def doQuery(info: RequestInfo, params: Map[String, String]): Response = {
    val headers = HeaderFilter.headers(info.req)

    val jsonReq = { base: RequestBuilder =>
      val req = base.
        addPaths(Seq("id", s"${info.datasetId}.soqlpack")).
        addHeaders(headers).
        addHeader(ReqIdHeader -> info.requestId).
        query(params).get
      logger.info(URLDecoder.decode(req.toString, UTF_8.name))
      req
    }

    // TODO: Hacks!  Ideally would use unpackFeatures as the callback.
    val cb = { raw: Response =>
      val is = Option(raw.inputStream())
      val bytes = is.map(IOUtils.toByteArray(_)).getOrElse(Array.empty)
      new StandardResponse(raw, new ByteArrayInputStream(bytes))
    }

    client.execute(jsonReq, cb)
  }
}

object GeoProvider {
  /** Type of callback we will be passing to `client`. */
  type Callback = Response => HttpResponse

  private[util] val logger: Logger = LoggerFactory.getLogger(getClass)

  // TODO: Remove the .clone() call here when this is combined with unpackFeatures.
  private[util] def rollup(tile: QuadTile,
                           features: => Iterator[FeatureJson]): Set[Feature] = {
    val pxCounts = new collection.mutable.HashMap[Feature, Int].withDefaultValue(0)
    features.foreach { f =>
      val geom = f.geometry.clone().asInstanceOf[Geometry]
      geom.apply(tile)
      geom.geometryChanged()

      pxCounts(geom -> f.properties) += 1
    }

    pxCounts.map { case ((geom, props), count) =>
      geom -> Map("count" -> toJValue(count), "properties" -> toJValue(props))
    } (collection.breakOut) // Build `Set` not `Seq`.
  }

  def unpackFeatures(rs: ResourceScope):
      Response => Try[Iterator[FeatureJson]] = { resp: Response =>
    val dis = rs.open(new DataInputStream(resp.inputStream(Long.MaxValue)))

    try {
      val soqlIter = new SoQLPackIterator(dis)
      if (soqlIter.geomIndex < 0) {
        Failure(InvalidSoqlPackException(
                  s"No geometry present or other header error: ${soqlIter.headers}"))
      } else {
        val featureJsonIter = soqlIter.map { row =>
          val soqlRow = new SoQLGeoRow(row, soqlIter)
          FeatureJson(soqlRow.properties, soqlRow.geometry)
        }

        Success(featureJsonIter)
      }
    } catch {
      case e @ (_: InvalidMsgPackDataException |
                  _: ClassCastException |
                  _: NoSuchElementException |
                  _: NullPointerException) =>
        Failure(InvalidSoqlPackException("Unable to parse binary stream into SoQLPack/MessagePack records", e))
    }
  }
}
