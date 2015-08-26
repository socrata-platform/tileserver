package com.socrata.tileserver
package mocks

import java.util.Collections
import java.util.Enumeration
import javax.servlet.http.HttpServletRequest
import scala.collection.JavaConverters._

import com.rojoma.simplearm.v2.ResourceScope
import org.mockito.Matchers.anyString
import org.mockito.Mockito.{mock, when}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

import com.socrata.http.server.HttpRequest
import com.socrata.http.server.HttpRequest.AugmentedHttpServletRequest

class StaticRequest(val params: Map[String, String],
                    val headers: Map[String, String]) extends HttpRequest {
  // Had to go all the way to HttpServletRequest to mock out queryParameters.
  private val underlyingReq = mock(classOf[HttpServletRequest])
  private val headerNames = headers.keys.toSeq

  when(underlyingReq.getQueryString()).
    thenReturn(params map { case (k, v) => s"$k=$v" } mkString "&")
  when(underlyingReq.getHeaderNames).
    thenReturn(Collections.enumeration(headerNames.asJava))

  // Headers are more complicated...
  when(underlyingReq.getHeaders(anyString)).thenAnswer(
    new Answer[Enumeration[String]] {
      override def answer(invocation: InvocationOnMock): Enumeration[String] = {
        val args = invocation.getArguments()
        args(0) match {
          case name: String => {
            val header = headers.asJava.get(name)
            Collections.enumeration(Seq(header).asJava)
          }
          case _ => throw new ClassCastException()
        }
      }
    })

  when(underlyingReq.getHeader(anyString)).thenAnswer(new Answer[String] {
      override def answer(invocation: InvocationOnMock): String = {
        val args = invocation.getArguments()
        args(0) match {
          case name: String => headers.asJava.get(name)
          case _ => throw new ClassCastException()
        }
      }
    })

  val servletRequest = new AugmentedHttpServletRequest(underlyingReq)

  val resourceScope: ResourceScope = new ResourceScope()

  // scalastyle:off method.name spaces.after.plus
  def +(param: (String, String)): StaticRequest =
    new StaticRequest(params + param, headers)

  def ++(other: StaticRequest): StaticRequest =
    new StaticRequest(params ++ other.params, headers)
  // scalastyle:on method.name spaces.after.plus
}

object StaticRequest {
  val httpServletRequest: HttpServletRequest = mock(classOf[HttpServletRequest])

  def apply(params: Map[String, String], headers: Map[String, String]): StaticRequest =
    new StaticRequest(params, headers)

  def apply(params: Map[String, String]): StaticRequest =
    apply(params, Map[String, String]())

  def apply(): StaticRequest = new StaticRequest(Map.empty, Map.empty)

  def apply(param: (String, Any)): StaticRequest = {
    val (k, v) = param
    new StaticRequest(Map(k -> v.toString), Map.empty)
  }

  def apply(param: (String, String), header: (String, String)): StaticRequest =
    new StaticRequest(Map(param), Map(header))

  def apply(param: (String, String), headers: Map[String, String]): StaticRequest =
    apply(Map(param), headers)
}
