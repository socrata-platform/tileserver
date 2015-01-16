package com.socrata.thirdparty.curator

import com.rojoma.simplearm.v2.{Managed, Resource}
import org.apache.curator.x.discovery.ServiceDiscovery

import com.socrata.http.client.HttpClientHttpClient

class DiscoveryBroker private[curator] (discovery: ServiceDiscovery[Void],
                                        httpClient: HttpClientHttpClient) {
  def clientFor(config: CuratedClientConfig): Managed[CuratedServiceClient] = {
    for {
      sp <- ServiceProviderFromName[Void](discovery, config.serviceName)
    } yield {
      val provider = CuratorServerProvider(httpClient, sp, identity)
      CuratedServiceClient(provider, config)
    }
  }
}
