package com.socrata.tileserver

import scala.language.implicitConversions

import org.scalacheck.Arbitrary
import org.scalacheck.Gen

import com.socrata.http.server.responses._

import util.CoordinateMapper.Size

// scalastyle:off import.grouping
package object gen {
  object ShortStrings {
    case class ShortString(underlying: String)

    implicit def sstrToString(a: ShortString): String = a.underlying
    implicit def sstrStringPair(p: (ShortString, ShortString)): (String, String) = {
      val (a, b) = p
      (a.underlying, b.underlying)
    }

    private val shortGen = for {
      length <- Gen.choose(0, 10)
      chars <- Gen.listOfN(length, Arbitrary.arbitrary[Char])
    } yield ShortString(chars.mkString(""))

    implicit val shortStr: Arbitrary[ShortString] = Arbitrary(shortGen)
  }

  object Alphanumerics {
    case class Alphanumeric(underlying: String)

    implicit def alnumToString(a: Alphanumeric): String = a.underlying
    implicit def alnumToStringPair(p: (Alphanumeric, Alphanumeric)): (String, String) = {
      val (a, b) = p
      (a.underlying, b.underlying)
    }

    private val alnumGen = for {
      length <- Gen.choose(0, 10)
      chars <- Gen.listOfN(length, Gen.alphaNumChar)
    } yield Alphanumeric(chars.mkString(""))

    implicit val alnum: Arbitrary[Alphanumeric] = Arbitrary(alnumGen)
  }

  object StatusCodes {
    trait StatusCodeLike {
      def underlying: Int
      def toInt: Int = underlying
    }

    case class KnownStatusCode(underlying: Int) extends StatusCodeLike
    case class UnknownStatusCode(underlying: Int) extends StatusCodeLike
    case class NotOkStatusCode(underlying: Int) extends StatusCodeLike

    implicit def statusCodeToInt(sc: StatusCodeLike): Int = sc.underlying

    private val knownStatusCodes = Set(BadRequest.statusCode,
                                       Forbidden.statusCode,
                                       NotFound.statusCode,
                                       RequestTimeout.statusCode,
                                       InternalServerError.statusCode,
                                       NotImplemented.statusCode,
                                       ServiceUnavailable.statusCode)

    private val knownScGen = for {
      statusCode <- Gen.oneOf(knownStatusCodes.toSeq)
    } yield (KnownStatusCode(statusCode))

    private val unknownScGen = for {
      statusCode <- Gen.choose(100, 599) suchThat { statusCode: Int =>
        !knownStatusCodes(statusCode) &&
          statusCode != OK.statusCode && statusCode != NotModified.statusCode
      }
    } yield (UnknownStatusCode(statusCode))

    private val notOkScGen = for {
      statusCode <- Gen.choose(100, 599) suchThat { statusCode: Int =>
        statusCode != OK.statusCode && statusCode != NotModified.statusCode
      }
    } yield (NotOkStatusCode(statusCode))

    implicit val knownSc: Arbitrary[KnownStatusCode] = Arbitrary(knownScGen)
    implicit val unknownSc: Arbitrary[UnknownStatusCode] = Arbitrary(unknownScGen)
    implicit val notOkSc: Arbitrary[NotOkStatusCode] = Arbitrary(notOkScGen)
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

    private val incomingGen = {
      Gen.oneOf(incoming.toSeq).flatMap { k =>
        if (k == socrata) ShortStrings.shortStr.arbitrary.map(k + _) else k
      }.flatMap { k =>
        ShortStrings.shortStr.arbitrary.map(IncomingHeader(k, _))
      }
    }

    private val outgoingGen = {
      Gen.oneOf(outgoing.toSeq).flatMap { k =>
        if (k == socrata) ShortStrings.shortStr.arbitrary.map(k + _) else k
      }.flatMap { k =>
        ShortStrings.shortStr.arbitrary.map(OutgoingHeader(k, _))
      }
    }

    private val unknownGen = for {
      k <- ShortStrings.shortStr.arbitrary suchThat { k =>
        !(k.startsWith(socrata) || incoming(k) || outgoing(k))
      }
      v <- ShortStrings.shortStr.arbitrary
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

    private val validGen = for {
      x <- Gen.choose(0, 255)
      y <- Gen.choose(0, 255)
    } yield ValidPoint(x, y)

    private val invalidGen = for {
      x <- Gen.choose(-256, 512) suchThat { x => x < 0 || x > 255 }
      y <- Gen.choose(-256, 512) suchThat { y => y < 0 || y > 255 }
    } yield InvalidPoint(x, y)

    implicit val valid: Arbitrary[ValidPoint] = Arbitrary(validGen)
    implicit val invalid: Arbitrary[InvalidPoint] = Arbitrary(invalidGen)
  }

  object Extensions {
    case class Extension(name: String)

    val Json = Extension("json")
    val Pbf = Extension("pbf")
    val BPbf = Extension("bpbf")
    val Png = Extension("png")
    val Txt = Extension("txt")

    private val extensions = Seq(Json, Pbf, BPbf, Png, Txt)

    implicit def extensionToString(e: Extension): String = e.name
    implicit val extension: Arbitrary[Extension] = Arbitrary(Gen.oneOf(extensions))
  }

  object QuadTiles {
    import util.QuadTile

    private val tileGen = for {
      zoom <- Gen.choose(1, 20)
      max = 1 << zoom - 1
      x <- Gen.choose(0, max)
      y <- Gen.choose(0, max)
    } yield QuadTile(x, y, zoom)

    implicit val quadTile: Arbitrary[QuadTile] = Arbitrary(tileGen)
  }

  object SmallMapTiles {
    import com.socrata.tileserver.util.RenderProvider.MapTile

    case class SmallMapTile(underlying: MapTile)

    implicit def smallMapTileToMapTile(mt: SmallMapTile): MapTile = mt.underlying

    private val layerNames = Seq("main",
                                 "point",
                                 "multipoint",
                                 "line",
                                 "multiline",
                                 "polygon",
                                 "multipolygon")

    private val featureGen: Gen[Map[String, Any]] = for {
      maxProps <- Gen.choose(0, 8)
      pairs <- Gen.listOfN(maxProps, Arbitrary.arbitrary[(String, String)])
    } yield pairs.toMap

    // Need to generate a JValue with the correct schema.
    private val layerGen: Gen[Seq[Map[String, Any]]] = for {
      maxProperties <- Gen.choose(0, 16)
      features <- Gen.listOfN(maxProperties, featureGen)
    } yield features

    private val tileGen: Gen[SmallMapTile] = for {
      layerNames <- Gen.someOf(layerNames)
      layers <- Gen.listOfN(layerNames.length, layerGen)
    } yield SmallMapTile(layerNames.zip(layers).toMap)

    // This should be layerName -> [Features] will need to choose from the set of valid layer names randomly.

    implicit val mapTile: Arbitrary[SmallMapTile] = Arbitrary(tileGen)
  }
}
