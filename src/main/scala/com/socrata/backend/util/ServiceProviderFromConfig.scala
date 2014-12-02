package com.socrata.backend
package util

import com.rojoma.simplearm.v2.Managed
import org.apache.curator.x.discovery.ServiceProvider
import org.apache.curator.x.discovery.{strategies => providerStrategies, ServiceDiscovery}

// TODO: Move to thirdparty utils
object ServiceProviderFromConfig {
  def apply[T](discovery: ServiceDiscovery[T], serviceName: String): Managed[ServiceProvider[T]] =
    new Managed[ServiceProvider[T]] {
      def run[A](f: ServiceProvider[T] => A): A = {
        val sp = discovery.serviceProviderBuilder().
          providerStrategy(new providerStrategies.RoundRobinStrategy).
          serviceName(serviceName).
          build()
        try {
          sp.start()
          f(sp)
        } finally {
          sp.close()
        }
      }
    }
}
