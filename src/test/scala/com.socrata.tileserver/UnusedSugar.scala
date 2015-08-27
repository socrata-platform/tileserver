package com.socrata.tileserver

import java.io.{ByteArrayInputStream, InputStream}
import scala.language.implicitConversions

import com.rojoma.simplearm.v2.ResourceScope
import org.mockito.Mockito.mock

import com.socrata.curator.CuratedServiceClient
import com.socrata.http.client.{HttpClient, RequestBuilder, Response}
import com.socrata.http.server.{HttpRequest, HttpResponse}
import com.socrata.thirdparty.geojson.FeatureJson

import util.{CartoRenderer, GeoProvider}

import UnusedSugar.UnusedValue

trait UnusedSugar {
  val Unused: UnusedValue = UnusedSugar.UnusedObj

  implicit def unusedToInt(u: UnusedValue): Int = 0
  implicit def unusedToString(u: UnusedValue): String = u.toString

  // Can't be Map[K, V] because then it matches K => V.
  implicit def unusedToMap[T](u: UnusedValue): Map[String, T] = Map.empty
  implicit def unusedToSet[T](u: UnusedValue): Set[T] = Set.empty
  implicit def unusedToOption[T](u: UnusedValue): Option[T] = None
  implicit def unusedToInputStream(u: UnusedValue): InputStream =
    new ByteArrayInputStream(Array.empty)

  implicit def unusedToRespToHttpResponse(u: UnusedValue): Response => HttpResponse =
    r => mock(classOf[HttpResponse])
  implicit def unusedToClient(u: UnusedValue): CuratedServiceClient =
    mock(classOf[CuratedServiceClient])
  implicit def unusedToResourceScope(u: UnusedValue): ResourceScope = UnusedSugar.rs
  implicit def unusedToHttpRequest(u: UnusedValue): HttpRequest =
    mocks.StaticRequest()
  implicit def unusedToRequestBuilder(u: UnusedValue): RequestBuilder =
    RequestBuilder(Unused)
  implicit def unusedToHttpClient(u: UnusedValue): HttpClient =
    mocks.StaticHttpClient(Unused)

  implicit def unusedToRenderer(u: UnusedValue): CartoRenderer =
    CartoRenderer(Unused, Unused)

  implicit def unusedToProvider(u: UnusedValue): GeoProvider = GeoProvider(Unused)

  implicit def unusedToQuadTile(u: UnusedValue): util.QuadTile =
    new util.QuadTile(0, 0, 0) {
      override def px(lon: Double, lat: Double): (Int, Int) = (lon.toInt, lat.toInt)
    }

  implicit def unusedToRequestINfo(u: UnusedValue): util.RequestInfo =
    util.RequestInfo(Unused, Unused, Unused, Unused, Unused)
}

object UnusedSugar extends UnusedSugar {
  trait UnusedValue {
    override val toString = "unused"
  }

  private object UnusedObj extends UnusedValue // There's lots of magic going on here.

  val rs = new ResourceScope()
}
