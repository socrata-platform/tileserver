package com.socrata.tileserver.mocks

case class HeaderResponse(underlying: Map[String, String])
    extends MsgPackResponse() {
  override val headerNames: Set[String] = underlying.keySet

  override def headers(key: String): Array[String] =
    if (underlying.contains(key)) Array(underlying(key)) else Array.empty
}
