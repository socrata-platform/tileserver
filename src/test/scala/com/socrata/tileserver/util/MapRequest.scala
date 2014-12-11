package com.socrata.tileserver.util

import javax.servlet.http.HttpServletRequest

import com.rojoma.simplearm.v2.ResourceScope
import org.mockito.Mockito.{mock, when}

import com.socrata.http.server.HttpRequest
import com.socrata.http.server.HttpRequest.AugmentedHttpServletRequest

class MapRequest(val params: Map[String, String]) extends HttpRequest {
  // Had to go all the way to HttpServletRequest to mock out queryParameters.
  private val underlyingServletRequest = mock(classOf[HttpServletRequest])
  when(underlyingServletRequest.getQueryString()).
    thenReturn(params map { case (k, v) => s"$k=$v" } mkString "&")

  val servletRequest = new AugmentedHttpServletRequest(underlyingServletRequest)

  val resourceScope: ResourceScope = new ResourceScope()

  // scalastyle:off method.name spaces.after.plus
  def +(param: (String, String)): MapRequest = new MapRequest(params + param)

  def ++(other: MapRequest): MapRequest =
    new MapRequest(params ++ other.params)
  // scalastyle:on method.name spaces.after.plus
}

object MapRequest {
  val httpServletRequest: HttpServletRequest = mock(classOf[HttpServletRequest])

  def apply(kv: (String, String)): MapRequest =
    new MapRequest(Map(kv))
}
