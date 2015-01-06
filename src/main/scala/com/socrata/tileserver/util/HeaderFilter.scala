package com.socrata.tileserver.util

import com.socrata.http.server.HttpRequest

object HeaderFilter {
  val inHeaders =
    Set("Authorization", "Cookie", "If-Modified-Since").map(_.toLowerCase)
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

    headerNames flatMap { name: String =>
      req.headers(name) map { (name, _) }
    } toIterable
  }
}
