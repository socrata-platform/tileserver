package com.socrata.tileserver.implicits

import scala.language.implicitConversions

import javax.servlet.http.HttpServletResponse.{SC_NOT_MODIFIED => ScNotModified}
import javax.servlet.http.HttpServletResponse.{SC_OK => ScOk}

import org.scalacheck.Arbitrary
import org.scalacheck.Gen

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
  val knownStatusCodes = Set(400, 401, 403, 404, 408, 500, 501, 503)

  val knownScGen = for {
    statusCode <- Gen.oneOf(knownStatusCodes.toSeq)
  } yield (KnownStatusCode(statusCode))

  val unknownScGen = for {
    statusCode <- Gen.choose(100, 599) suchThat { statusCode: Int =>
      !knownStatusCodes(statusCode) && statusCode != ScOk && statusCode != ScNotModified
    }
  } yield (UnknownStatusCode(statusCode))
  // scalastyle:on magic.number
  implicit val knownSc = Arbitrary(knownScGen)
  implicit val unknownSc = Arbitrary(unknownScGen)
}
