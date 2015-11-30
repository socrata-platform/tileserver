package com.socrata.tileserver
package mocks

import com.socrata.curator.CuratedServiceClient

import UnusedSugar._

class StaticGeoProvider(client: Option[CuratedServiceClient],
                        query: util.RequestInfo => util.GeoResponse)
    extends util.GeoProvider(client.getOrElse(Unused)) {
  private var thrown = Seq.empty[Throwable]
  def exceptions: Seq[Throwable] = thrown

  override def doQuery(info: util.RequestInfo): util.GeoResponse = {
    try {
      super.doQuery(info)
      query(info)
    } catch {
      case t: Throwable =>
        thrown = t +: thrown
        throw t
    }
  }
}

object StaticGeoProvider {
  def apply(client: CuratedServiceClient)(resp: util.GeoResponse): StaticGeoProvider =
    new StaticGeoProvider(Some(client), _ => resp)

  def apply(query: util.RequestInfo => util.GeoResponse): StaticGeoProvider =
    new StaticGeoProvider(None, query)
  def apply(resp: util.GeoResponse): StaticGeoProvider = apply(_ => resp)

  def apply(): StaticGeoProvider = apply(StaticGeoResponse())
}
