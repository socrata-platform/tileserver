package com.socrata.tileserver

import scala.language.implicitConversions

import com.rojoma.simplearm.v2.ResourceScope
import org.mockito.Mockito.mock

import com.socrata.http.client.Response
import com.socrata.http.server.{HttpRequest, HttpResponse}
import com.socrata.thirdparty.curator.CuratedServiceClient

trait UnusedSugar {
  trait UnusedValue
  object Unused extends UnusedValue
  implicit def unusedToInt(u: UnusedValue): Int = 0
  implicit def unusedToString(u: UnusedValue): String = "unused"
  implicit def unusedToHttpRequest(u: UnusedValue): HttpRequest =
    mocks.StaticRequest()
  implicit def unusedToQuadTile(u: UnusedValue): util.QuadTile =
    new util.QuadTile(0, 0, 0) {
      override def px(lon: Double, lat: Double): Option[(Int, Int)] =
        Some((lon.toInt, lat.toInt))
    }
  implicit def unusedToClient(u: UnusedValue): CuratedServiceClient =
    mock(classOf[CuratedServiceClient])
  implicit def unusedToRespToHttpResponse(u: UnusedValue): Response => HttpResponse =
    r => mock(classOf[HttpResponse])
  // Can't be Map[K, V] because then it matches K => V.
  implicit def unusedToMap[T](u: UnusedValue): Map[String, T] = Map.empty
  implicit def unusedToSet[T](u: UnusedValue): Set[T] = Set.empty

  implicit def unusedToResourceScope(u: UnusedValue): ResourceScope = UnusedSugar.rs
}

object UnusedSugar {
  val rs = new ResourceScope()
}
