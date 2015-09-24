package com.socrata.tileserver.util

import com.socrata.http.client.ResponseInfo
import com.socrata.http.server.implicits._
import com.socrata.http.server.responses._
import com.socrata.http.server.{HttpRequest, HttpResponse}

/** Removes all headers that aren't known to be safe to duplicate. */
object HeaderFilter {
  private val inHeaders =
    Set("Authorization", "Cookie", "If-Modified-Since", "X-App-Token").map(_.toLowerCase)
  private val outHeaders =
    Set("Cache-Control", "Expires", "Last-Modified").map(_.toLowerCase)

  private def internal(name: String): Boolean =
    name.toLowerCase.startsWith("x-socrata-")

  private def incoming(name: String): Boolean =
    inHeaders(name.toLowerCase) || internal(name)

  private def outgoing(name: String): Boolean =
    outHeaders(name.toLowerCase) || internal(name)

  /** Filter incoming headers.
    *
    * @param req the incoming request.
    */
  def headers(req: HttpRequest): Iterable[(String, String)] = {
    val headerNames = req.headerNames filter { name: String =>
      incoming(name)
    }

    val host = req.header("Host")
    val socrataHost = req.header("X-Socrata-Host").orElse(host)

    val filtered = headerNames.flatMap { name: String =>
      req.headers(name) map { (name, _) }
    } ++ socrataHost.map("X-Socrata-Host" -> _)

    filtered.toIterable
  }

  /** Filter outgoing headers.
    *
    * @param req the outgoing response.
    */
  def headers(resp: ResponseInfo): Iterable[(String, String)] = {
    val headerNames = resp.headerNames filter { name: String =>
      outgoing(name)
    }

    headerNames.flatMap { name: String =>
      resp.headers(name) map { (name, _) }
    }
  }

  /** Extract headers for a HttpResponse (filtering them).
    *
    * @param resp the underlying response to pull headers from.
    */
  def extract(resp: ResponseInfo): HttpResponse =
    headers(resp).map({ case (k, v) => Header(k, v) }).fold(NoOp)(_ ~> _)
}
