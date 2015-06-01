package com.socrata.tileserver.util

import com.socrata.http.client.Response
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.{HttpRequest, HttpResponse}

object HeaderFilter {
  val inHeaders =
    Set("Authorization", "Cookie", "If-Modified-Since", "X-App-Token").map(_.toLowerCase)
  val outHeaders =
    Set("Cache-Control", "Expires", "Last-Modified").map(_.toLowerCase)

  private def internal(name: String): Boolean =
    name.toLowerCase.startsWith("x-socrata-")

  private def incoming(name: String): Boolean =
    inHeaders(name.toLowerCase) || internal(name)

  private def outgoing(name: String): Boolean =
    outHeaders(name.toLowerCase) || internal(name)

  def headers(req: HttpRequest): Iterable[(String, String)] = {
    val headerNames = req.headerNames filter { name: String =>
      incoming(name)
    }

    headerNames.flatMap { name: String =>
      req.headers(name) map { (name, _) }
    }.toIterable
  }

  def headers(resp: Response): Iterable[(String, String)] = {
    val headerNames = resp.headerNames filter { name: String =>
      outgoing(name)
    }

    headerNames.flatMap { name: String =>
      resp.headers(name) map { (name, _) }
    }.toIterable
  }

  def extract(resp: Response): HttpResponse =
    headers(resp).map({ case (k, v) => Header(k, v) }).fold(NoOp)(_ ~> _)
}
