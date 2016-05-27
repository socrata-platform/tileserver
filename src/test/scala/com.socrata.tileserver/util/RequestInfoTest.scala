package com.socrata.tileserver
package util

class RequestInfoTest extends TestBase with UnusedSugar {
  val keys = Seq("$SELECT", "$WHERE", "$GROUPBY", "$LIMIT", "$STYLE", "$OFFSET", "$MONDARA")

  test("Parameters that begin with `$` are downcased") {
    import gen.Alphanumerics._

    forAll { rawVal: Alphanumeric =>
      val value: String = rawVal
      keys.foreach { key =>
        val request = mocks.StaticRequest(key -> value)
        val info = RequestInfo(request, Unused, Unused, Unused, Unused)

        info.queryParameters.toSet must contain (key.toLowerCase -> value)
      }
    }
  }

  test("Parameters that do not begin with `$` are passed through") {
    import gen.Alphanumerics._

    forAll { (rawKey: Alphanumeric, rawValue: Alphanumeric) =>
      val key: String = rawKey
      val value: String = rawValue
      val request = mocks.StaticRequest(key -> value)
      val info = RequestInfo(request, Unused, Unused, Unused, Unused)

      info.queryParameters.toSet must contain (key -> value)
    }
  }

  test("Extracting $style is case insensitive") {
    import gen.Alphanumerics._

    forAll { rawStyle: Alphanumeric =>
      val style: String = rawStyle
      val request = mocks.StaticRequest("$Style" -> style)
      val info = RequestInfo(request, Unused, Unused, Unused, Unused)

      info.style must be (Some(style))
    }
  }

  test("Extracting $overscan is case insensitive") {
    forAll { rawOverscan: Int =>
      val overscan: String = rawOverscan.toString
      val request = mocks.StaticRequest("$Overscan" -> overscan)
      val info = RequestInfo(request, Unused, Unused, Unused, Unused)

      info.overscan must be (Some(rawOverscan))
    }
  }

  test("Extracting $mondara is case insensitive") {
    val request = mocks.StaticRequest("$Mondara" -> "true")
    val info = RequestInfo(request, Unused, Unused, Unused, Unused)

    info.mondaraHack must be (true)
  }
}
