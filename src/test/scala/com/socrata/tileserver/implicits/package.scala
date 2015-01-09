package com.socrata.tileserver

import scala.language.implicitConversions

import javax.servlet.http.HttpServletResponse.{SC_NOT_MODIFIED => ScNotModified}
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}

import org.scalacheck.Arbitrary
import org.scalacheck.Gen

package object implicits {
  object StatusCodes {
    case class KnownStatusCode(val underlying: Int) {
      override val toString: String = underlying.toString
    }
    implicit def knownStatusCodeToInt(k: KnownStatusCode): Int = k.underlying

    case class UnknownStatusCode(val underlying: Int) {
      override val toString: String = underlying.toString
    }
    implicit def unknownStatusCodeToInt(u: UnknownStatusCode): Int = u.underlying

    // scalastyle:off magic.number
    private val knownStatusCodes: Set[Int] = Set(400, 403, 404, 408, 500, 501, 503)

    private val knownScGen: Gen[KnownStatusCode] = for {
      statusCode <- Gen.oneOf(knownStatusCodes.toSeq)
    } yield (KnownStatusCode(statusCode))

    private val unknownScGen: Gen[UnknownStatusCode] = for {
      statusCode <- Gen.choose(100, 599) suchThat { statusCode: Int =>
        !knownStatusCodes(statusCode) && statusCode != ScOk && statusCode != ScNotModified
      }
    } yield (UnknownStatusCode(statusCode))
    // scalastyle:on magic.number
    implicit val knownSc: Arbitrary[KnownStatusCode] = Arbitrary(knownScGen)
    implicit val unknownSc: Arbitrary[UnknownStatusCode] = Arbitrary(unknownScGen)
  }

  object Headers {
    sealed trait Header {
      def key: String
      def value: String
    }

    case class IncomingHeader(key: String, value: String) extends Header
    case class OutgoingHeader(key: String, value: String) extends Header
    case class UnknownHeader(key: String, value: String) extends Header
    implicit def headerToTuple(h: Header): (String, String) = (h.key, h.value)

    private val socrata: String = "X-Socrata-"
    private val incoming: Set[String] = Set("Authorization",
                                            "Cookie",
                                            "If-Modified-Since",
                                            socrata)
    private val outgoing: Set[String] = Set("Cache-Control",
                                            "Expires",
                                            "Last-Modified",
                                            socrata)

    private val incomingGen: Gen[IncomingHeader] = for {
      k <- Gen.oneOf(incoming.toSeq)
      k2 <- Arbitrary.arbString.arbitrary
      v <- Arbitrary.arbString.arbitrary
    } yield if (k == socrata) IncomingHeader(k + k2, v) else IncomingHeader(k, v)

    private val outgoingGen: Gen[OutgoingHeader] = for {
      k <- Gen.oneOf(outgoing.toSeq)
      k2 <- Arbitrary.arbString.arbitrary
      v <- Arbitrary.arbString.arbitrary
    } yield if (k == socrata) OutgoingHeader(k + k2, v) else OutgoingHeader(k, v)

    private val unknownGen: Gen[UnknownHeader] = for {
      k <- Arbitrary.arbString.arbitrary suchThat { k =>
        !(k.startsWith(socrata) || incoming(k) || outgoing(k))
      }
      v <- Arbitrary.arbString.arbitrary
    } yield UnknownHeader(k, v)

    implicit val incomingHeader: Arbitrary[IncomingHeader] = Arbitrary(incomingGen)
    implicit val outgoingHeader: Arbitrary[OutgoingHeader] = Arbitrary(outgoingGen)
    implicit val unknownHeader: Arbitrary[UnknownHeader] = Arbitrary(unknownGen)
  }
}
