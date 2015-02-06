package com.socrata.tileserver

import scala.language.implicitConversions
import javax.servlet.http.HttpServletResponse._

import org.scalacheck.Arbitrary
import org.scalacheck.Gen

import util.CoordinateMapper.Size

package object gen {
  object StatusCodes {
    case class KnownStatusCode(val underlying: Int) {
      override val toString: String = underlying.toString
    }
    implicit def knownStatusCodeToInt(k: KnownStatusCode): Int = k.underlying

    case class UnknownStatusCode(val underlying: Int) {
      override val toString: String = underlying.toString
    }
    implicit def unknownStatusCodeToInt(u: UnknownStatusCode): Int = u.underlying

    private val knownStatusCodes = Set(SC_BAD_REQUEST,
                                       SC_FORBIDDEN,
                                       SC_NOT_FOUND,
                                       SC_REQUEST_TIMEOUT,
                                       SC_INTERNAL_SERVER_ERROR,
                                       SC_NOT_IMPLEMENTED,
                                       SC_SERVICE_UNAVAILABLE)

    private val knownScGen = for {
      statusCode <- Gen.oneOf(knownStatusCodes.toSeq)
    } yield (KnownStatusCode(statusCode))

    // scalastyle:off magic.number
    private val unknownScGen = for {
      statusCode <- Gen.choose(100, 599) suchThat { statusCode: Int =>
        !knownStatusCodes(statusCode) &&
          statusCode != SC_OK && statusCode != SC_NOT_MODIFIED
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

    private val socrata = "X-Socrata-"
    private val incoming = Set("Authorization",
                               "Cookie",
                               "If-Modified-Since",
                               socrata)
    private val outgoing = Set("Cache-Control",
                               "Expires",
                               "Last-Modified",
                               socrata)

    private val incomingGen = for {
      k <- Gen.oneOf(incoming.toSeq)
      k2 <- Arbitrary.arbString.arbitrary
      v <- Arbitrary.arbString.arbitrary
    } yield if (k == socrata) IncomingHeader(k + k2, v) else IncomingHeader(k, v)

    private val outgoingGen = for {
      k <- Gen.oneOf(outgoing.toSeq)
      k2 <- Arbitrary.arbString.arbitrary
      v <- Arbitrary.arbString.arbitrary
    } yield if (k == socrata) OutgoingHeader(k + k2, v) else OutgoingHeader(k, v)

    private val unknownGen = for {
      k <- Arbitrary.arbString.arbitrary suchThat { k =>
        !(k.startsWith(socrata) || incoming(k) || outgoing(k))
      }
      v <- Arbitrary.arbString.arbitrary
    } yield UnknownHeader(k, v)

    implicit val incomingHeader: Arbitrary[IncomingHeader] = Arbitrary(incomingGen)
    implicit val outgoingHeader: Arbitrary[OutgoingHeader] = Arbitrary(outgoingGen)
    implicit val unknownHeader: Arbitrary[UnknownHeader] = Arbitrary(unknownGen)
  }

  object Points {
    sealed trait PointLike {
      def x: Int
      def y: Int

      /* Returns (lon, lat) */
      def onto(tile: util.QuadTile): (Double, Double) = {
        val mapper = tile.mapper
        val tx = tile.x * Size + x
        val ty = tile.y * Size + (Size - y)
        (mapper.lon(tx), mapper.lat(ty))
      }
    }

    case class Point(x: Int, y: Int) extends PointLike
    case class ValidPoint(x: Int, y: Int) extends PointLike
    case class InvalidPoint(x: Int, y: Int) extends PointLike

    implicit def pointToTuple(pt: PointLike): (Int, Int) = (pt.x, pt.y)
    // scalastyle:off magic.number
    private val validGen = for {
      x <- Gen.choose(0, 255)
      y <- Gen.choose(0, 255)
    } yield ValidPoint(x, y)

    private val invalidGen = for {
      x <- Gen.choose(-256, 512) suchThat { x => x < 0 || x > 255 }
      y <- Gen.choose(-256, 512) suchThat { y => y < 0 || y > 255 }
    } yield InvalidPoint(x, y)
    // scalastyle:on magic.number

    implicit val valid: Arbitrary[ValidPoint] = Arbitrary(validGen)
    implicit val invalid: Arbitrary[InvalidPoint] = Arbitrary(invalidGen)
  }

  object Extensions {
    case class Extension(name: String)

    val Json = Extension("json")
    val Pbf = Extension("pbf")
    val BPbf = Extension("bpbf")
    val Txt = Extension("txt")

    private val extensions = Seq(Json, Pbf, BPbf, Txt)

    implicit def extensionToString(e: Extension): String = e.name
    implicit val extension: Arbitrary[Extension] = Arbitrary(Gen.oneOf(extensions))
  }

  object QuadTiles {
    import util.QuadTile

    // scalastyle:off magic.number
    private val tileGen = for {
      zoom <- Gen.choose(1, 20)
      max = 1 << zoom - 1
      x <- Gen.choose(0, max)
      y <- Gen.choose(0, max)
    } yield QuadTile(x, y, zoom)
    // scalastyle:on magic.number

    implicit val quadTile: Arbitrary[QuadTile] = Arbitrary(tileGen)
  }
}
